// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.common.TestApplicationKt;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.actions.RemoveUsageAction;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UsageViewTest extends BasePlatformTestCase {
  public void testUsageViewDoesNotHoldPsiFilesOrDocuments() {
    // sick and tired of hunting tests leaking documents
    ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();

    Set<Object> alreadyLeaking = new ReferenceOpenHashSet<>();
    Predicate<Object> isReallyLeak = file -> {
      if (file instanceof PsiFile) {
        if (!((PsiFile)file).isPhysical()) {
          return false;
        }
        Project project = ((PsiFile)file).getProject();
        if (alreadyLeaking.add(project)) {
          System.err.println(project + " already leaking; its creation trace: " + ((ProjectEx)project).getCreationTrace());
        }
      }
      alreadyLeaking.add(file);
      return false;
    };
    LeakHunter.checkLeak(ApplicationManager.getApplication(), PsiFileImpl.class, isReallyLeak);
    LeakHunter.checkLeak(ApplicationManager.getApplication(), Document.class, isReallyLeak);

    @Language("JAVA")
    String text = "public class X{} //iuggjhfg";
    PsiFile psiFile = myFixture.addFileToProject("X.java", text);
    Usage[] usages = new Usage[100];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(psiFile, i);
    }

    UsageView usageView = createUsageView(usages);

    TestApplicationKt.clearEncodingManagerDocumentQueue(ApplicationManager.getApplication());
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    LeakHunter.checkLeak(usageView, PsiFileImpl.class, file -> !alreadyLeaking.contains(file) && file.isPhysical());
    LeakHunter.checkLeak(usageView, Document.class, document -> !alreadyLeaking.contains(document));
  }

  public void testUsageViewHandlesDocumentChange() {
    @Language("JAVA")
    String text = "public class X{ int xxx; } //comment";
    PsiFile psiFile = myFixture.addFileToProject("X.java", text);
    Usage usage = createUsage(psiFile, psiFile.getText().indexOf("xxx"));

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "/* sdfsdfsd */"));
    documentManager.commitAllDocuments();
    int navigationOffset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
    assertEquals(psiFile.getText().indexOf("xxx"), navigationOffset);
  }

  public void testTextUsageInfoHandlesDocumentChange() {
    @Language("JAVA")
    String text = "public class X{ int xxx; } //comment";
    PsiFile psiFile = myFixture.addFileToProject("X.java", text);
    UsageInfo2UsageAdapter usage = new UsageInfo2UsageAdapter(
      new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(), "xxx")));

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "/* sdfsdfsd */"));
    documentManager.commitAllDocuments();
    int navigationOffset = usage.getUsageInfo().getNavigationOffset();
    assertEquals(psiFile.getText().indexOf("xxx"), navigationOffset);
  }

  private static Usage createUsage(PsiFile psiFile, int offset) {
    PsiElement element = psiFile.findElementAt(offset % psiFile.getTextLength());
    assertNotNull(element);
    return new UsageInfo2UsageAdapter(new UsageInfo(element));
  }

  public void testUsageViewCanRerunAfterTargetWasInvalidatedAndRestored() {
    @Language("JAVA")
    String fileText = """
      public class X{    void foo() {
              bar();
              bar();
          }    void bar() {}
      }""";
    PsiFile psiFile = myFixture.addFileToProject("X.java", fileText);

    PsiElement[] members = psiFile.getChildren()[psiFile.getChildren().length - 1].getChildren();
    PsiNamedElement bar = (PsiNamedElement)members[members.length - 3];
    assertEquals("bar", bar.getName());

    FindUsagesManager usagesManager = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager();
    FindUsagesHandler handler = usagesManager.getNewFindUsagesHandler(bar, false);
    UsageViewImpl usageView =
      (UsageViewImpl)usagesManager
        .doFindUsages(new PsiElement[]{bar}, PsiElement.EMPTY_ARRAY, handler, handler.getFindUsagesOptions(), false);
    waitForUsages(usageView);

    Disposer.register(myFixture.getTestRootDisposable(), usageView);

    assertTrue(usageView.canPerformReRun());

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    String barDef = "void bar() {}\n";
    String commentedBarDef = "//" + barDef;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String text = document.getText();
      document.replaceString(text.indexOf(barDef), text.indexOf(barDef) + barDef.length(), commentedBarDef);
    });
    documentManager.commitAllDocuments();
    assertFalse(usageView.canPerformReRun()); // target invalidated

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String text = document.getText();
      document.replaceString(text.indexOf(commentedBarDef), text.indexOf(commentedBarDef) + commentedBarDef.length(), barDef);
    });
    documentManager.commitAllDocuments();

    assertTrue(usageView.canPerformReRun());

    UsageView newView = usageView.doReRunTestAccessor();
    Disposer.register(myFixture.getTestRootDisposable(), newView);
    Set<Usage> usages = newView.getUsages();
    assertEquals(2, usages.size());
  }

  private void waitForUsages(UsageViewImpl usageView) {
    ProgressManager.getInstance().run(new Task.Modal(getProject(), "Waiting", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        usageView.waitForUpdateRequestsCompletion();
        usageView.drainQueuedUsageNodes();
      }
    });
  }

  public void testExcludeUsageMustExcludeChildrenAndParents() {
    @Language("JAVA")
    String fileText = "public class X{ int xxx; } //comment";
    PsiFile psiFile = myFixture.addFileToProject("X.java", fileText);
    Usage usage = new UsageInfo2UsageAdapter(
      new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(), "xxx")));

    UsageViewImpl usageView = createUsageView(usage);

    usageView.excludeUsages(new Usage[]{usage});
    UIUtil.dispatchAllInvocationEvents();

    Set<Node> excluded = new HashSet<>();
    Node[] usageNode = new Node[1];
    TreeUtil.traverse(usageView.getRoot(), node -> {
      if (((Node)node).isExcluded()) {
        excluded.add((Node)node);
      }
      if (node instanceof UsageNode && ((UsageNode)node).getUsage() == usage) {
        usageNode[0] = (UsageNode)node;
      }
      return true;
    });

    Set<Node> expectedExcluded = new HashSet<>();
    for (TreeNode n = usageNode[0]; n != usageView.getRoot(); n = n.getParent()) {
      expectedExcluded.add((Node)n);
    }
    assertEquals(expectedExcluded, excluded);


    usageView.includeUsages(new Usage[]{usage});
    UIUtil.dispatchAllInvocationEvents();

    excluded.clear();
    TreeUtil.traverse(usageView.getRoot(), node -> {
      if (((Node)node).isExcluded()) {
        excluded.add((Node)node);
      }
      return true;
    });

    assertEmpty(excluded);

    String text = new ExporterToTextFile(usageView, UsageViewSettings.getInstance()).getReportText();
    assertEquals("""
                   Usages  (1 usage found)
                       Unclassified  (1 usage found)
                           light_idea_test_case  (1 usage found)
                                 (1 usage found)
                                   X.java  (1 usage found)
                                       1 public class X{ int xxx; } //comment
                   """, StringUtil.convertLineSeparators(text));
  }

  @NotNull
  private UsageViewImpl createUsageView(Usage... usages) {
    return createUsageView(null, usages);
  }

  @NotNull
  private UsageViewImpl createUsageView(@Nullable UsageTarget target, Usage... usages) {
    UsageViewImpl usageView =
      (UsageViewImpl)UsageViewManager.getInstance(getProject())
        .createUsageView(target == null ? UsageTarget.EMPTY_ARRAY : new UsageTarget[]{target}, usages, new UsageViewPresentation(), null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);
    waitForUsages(usageView);
    UIUtil.dispatchAllInvocationEvents();

    return usageView;
  }

  public void testExcludeNodeMustExcludeChildrenAndParents() {
    @Language("JAVA")
    String text = "public class X{ int xxx; } //comment";
    PsiFile psiFile = myFixture.addFileToProject("X.java", text);
    Usage usage = new UsageInfo2UsageAdapter(
      new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(), "xxx")));

    UsageViewImpl usageView = createUsageView(usage);

    Node[] usageNode = new Node[1];
    TreeUtil.traverse(usageView.getRoot(), node -> {
      if (node instanceof UsageNode && ((UsageNode)node).getUsage() == usage) {
        usageNode[0] = (UsageNode)node;
      }
      return true;
    });
    Node nodeToExclude = (Node)usageNode[0].getParent();

    JComponent component = usageView.getComponent();
    DataContext dataContext = IdeUiService.getInstance().createUiDataContext(component);
    ExclusionHandler exclusionHandler = ExclusionHandler.EXCLUSION_HANDLER.getData(dataContext);
    exclusionHandler.excludeNode(nodeToExclude);
    UIUtil.dispatchAllInvocationEvents();

    Set<Node> excluded = new HashSet<>();
    TreeUtil.traverse(usageView.getRoot(), node -> {
      if (((Node)node).isExcluded()) {
        excluded.add((Node)node);
      }
      return true;
    });

    Set<Node> expectedExcluded = new HashSet<>();
    for (TreeNode n = usageNode[0]; n != usageView.getRoot(); n = n.getParent()) {
      expectedExcluded.add((Node)n);
    }
    assertEquals(expectedExcluded, excluded);


    exclusionHandler.includeNode(nodeToExclude);
    UIUtil.dispatchAllInvocationEvents();

    excluded.clear();
    TreeUtil.traverse(usageView.getRoot(), node -> {
      if (((Node)node).isExcluded()) {
        excluded.add((Node)node);
      }
      return true;
    });

    assertEmpty(excluded);
  }

  public void testUsageOfDecompiledOrBinaryElementMustNotRequestDecompilationDuringTreeRendering() {
    BinaryFileDecompiler decompiler = file -> {
      throw new IllegalStateException("oh no");
    };

    ExtensionTestUtil.addExtension((ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea(),
                                   BinaryFileTypeDecompilers.getInstance(),
                                   new FileTypeExtensionPoint<>(ArchiveFileType.INSTANCE.getName(), decompiler));

    PsiFile psiFile = myFixture.addFileToProject("X.jar", "xxx");
    assertEquals(ArchiveFileType.INSTANCE, psiFile.getFileType());
    assertTrue(psiFile.getFileType().isBinary());

    UsageInfo2UsageAdapter usage = new UsageInfo2UsageAdapter(new UsageInfo(psiFile));
    UsageViewImpl usageView = createUsageView(usage);

    usageView.expandAll();
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(usage.isValid());
    assertSame(psiFile, usage.getElement());
    assertTrue(usage.canNavigateToSource());
    assertTrue(usage.canNavigate());
    assertEquals(psiFile, usage.getUsageInfo().getElement());
    assertEquals(psiFile.getVirtualFile(), usage.getFile());
    assertEquals(1, usage.getMergedInfos().length);
    usage.getPresentation();
    assertEquals("Psi Binary File Impl X.jar", usage.getPlainText());
    assertEquals(UsageType.UNCLASSIFIED, usage.getUsageType());
    usage.getText();
    usage.getIcon();
    usage.getLocation();
    usage.getTooltipText();
    usage.getLibraryEntry();
    usage.getUsageInfo().getRangeInElement();
    usage.getUsageInfo().getElement();
    usage.getUsageInfo().getNavigationOffset();
  }

  private static String getPresentableText(Node node) {
    final var presentation = node.getCachedPresentation();
    if (presentation == null) return "";
    return Arrays.stream(presentation.getText())
      .map(it -> it.getText())
      .collect(Collectors.joining());
  }

  // If the presentation changes, it also needs to be updated here
  private static List<Usage> lookupUsagesWithPresentationsContainingString(UsageViewImpl usageView, String lookup) {
    final var usageNodesList = new ArrayList<Usage>();
    for (TreeNode node : TreeUtil.treeNodeTraverser(usageView.getRoot()).traverse(TreeTraversal.PRE_ORDER_DFS)) {
      if (!(node instanceof UsageNode nodeNode)) continue;
      final var nodePresentedText = getPresentableText(nodeNode);
      if (nodePresentedText.contains(lookup)) {
        usageNodesList.add(nodeNode.getUsage());
      }
    }
    return usageNodesList;
  }

  public void testDoubleRemovalRemovesThingsOk() {
    final var identifierString = "xxx";
    @Language("JAVA")
    final var text = """
      class X {
        public int xxx;
      
        void usage1() {
            System.out.println("Usage 1 " + xxx);
        }
      
        void usage2() {
            System.out.println("Usage 2 " + xxx);
        }
      }
      """;

    final var psiFile = myFixture.addFileToProject("X.java", text);
    final var psiFileText = psiFile.getText();
    final var firstOccurenceStart = psiFileText.indexOf(identifierString);
    final var secondOccurenceStart = psiFileText.indexOf(identifierString, firstOccurenceStart + identifierString.length());
    final var thirdOccurenceStart = psiFileText.indexOf(identifierString, secondOccurenceStart + identifierString.length());

    final var usageOne = new UsageInfo2UsageAdapter(
      new UsageInfo(psiFile, secondOccurenceStart, secondOccurenceStart + identifierString.length())
    );
    final var usageTwo = new UsageInfo2UsageAdapter(
      new UsageInfo(psiFile, thirdOccurenceStart, thirdOccurenceStart + identifierString.length())
    );

    UsageViewImpl usageView = createUsageView(usageOne, usageTwo);

    final var usageNodesListInitial = lookupUsagesWithPresentationsContainingString(usageView, "xxx);");

    assertEquals(2, usageNodesListInitial.size());

    final var firstNode = usageNodesListInitial.getFirst();
    RemoveUsageAction.process(new Usage[]{firstNode}, usageView);

    final var usageNodeListAfterRemove = lookupUsagesWithPresentationsContainingString(usageView, "xxx);");

    assertEquals(1, usageNodeListAfterRemove.size());
    // Assert on the structure of the tree after the remove
    // Must have a node with a presentation containing "Usage"
    // All nodes must be of the Node type
    // There must be a single leaf; The tree must be linear
    // The leaf must be of type UsageNode, and it must contain the correct usage
    boolean foundProjectFileUsagesRoot = false;
    for (TreeNode node : TreeUtil.treeNodeTraverser(usageView.getRoot()).traverse(TreeTraversal.PRE_ORDER_DFS)) {
      if (!(node instanceof Node nodeNode)) continue;
      final var nodeText = getPresentableText(nodeNode);

      if (nodeText.contains("Usages")) {
        foundProjectFileUsagesRoot = true;
        var traversalNode = nodeNode;

        while (traversalNode.getChildCount() != 0) {
          assertEquals(1, node.getChildCount());
          final var onlyChild = traversalNode.getChildAt(0);
          if (!(onlyChild instanceof Node)) {
            fail("Expected all nodes to be of type Node, got type " + onlyChild.getClass().getName());
            return;
          }
          traversalNode = (Node)onlyChild;
        }

        if (!(traversalNode instanceof UsageNode usageNode)) {
          fail("Expected the leaf node to be a UsageNode, got type " + traversalNode.getClass().getName());
          return;
        }

        assertTrue(getPresentableText(usageNode).contains("\"Usage 2 \""));
        break;
      }
    }
    assertTrue(foundProjectFileUsagesRoot);

    final var firstNodeAfterRemove = usageNodeListAfterRemove.getFirst();
    RemoveUsageAction.process(new Usage[]{firstNodeAfterRemove}, usageView);

    final var usageNodeListAfterDoubleRemove = lookupUsagesWithPresentationsContainingString(usageView, "xxx);");
    assertEquals(0, usageNodeListAfterDoubleRemove.size());

    boolean foundProjectFileUsagesRootAfterDoubleRemove = false;
    for (TreeNode node : TreeUtil.treeNodeTraverser(usageView.getRoot()).traverse(TreeTraversal.PRE_ORDER_DFS)) {
      if (!(node instanceof Node nodeNode)) continue;
      final var nodePresentableText = getPresentableText(nodeNode);
      if (nodePresentableText.contains("Usages")) {
        foundProjectFileUsagesRootAfterDoubleRemove = true;
        break;
      }
    }
    assertFalse(foundProjectFileUsagesRootAfterDoubleRemove);
  }
}
