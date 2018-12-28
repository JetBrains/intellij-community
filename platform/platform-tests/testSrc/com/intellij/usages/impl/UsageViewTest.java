// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

public class UsageViewTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testUsageViewDoesNotHoldPsiFilesOrDocuments() {
    boolean[] foundLeaksBeforeTest = new boolean[1];
    LeakHunter.checkLeak(ApplicationManager.getApplication(), PsiFileImpl.class, file -> {
      if (!file.isPhysical()) return false;
      System.err.println("DON'T BLAME ME, IT'S NOT MY FAULT! SOME SNEAKY TEST BEFORE ME HAS LEAKED PsiFiles!");
      foundLeaksBeforeTest[0] = true;
      return true;
    });

    if (foundLeaksBeforeTest[0]) {
      fail("Can't start the test: leaking PsiFiles found");
    }

    @Language("JAVA")
    String text = "public class X{} //iuggjhfg";
    PsiFile psiFile = myFixture.addFileToProject("X.java", text);
    Usage[] usages = new Usage[100];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(psiFile, i);
    }

    UsageView usageView = createUsageView(usages);

    LightPlatformTestCase.clearEncodingManagerDocumentQueue();
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    LeakHunter.checkLeak(usageView, PsiFileImpl.class, PsiFileImpl::isPhysical);
    LeakHunter.checkLeak(usageView, Document.class);
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
    String fileText = "public class X{" +
                      "    void foo() {\n" +
                      "        bar();\n" +
                      "        bar();\n" +
                      "    }" +
                      "    void bar() {}\n" +
                      "}";
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

    UsageView newView = usageView.doReRun();
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
    assertEquals("Found usages  (1 usage found)\n" +
                 "    Unclassified usage  (1 usage found)\n" +
                 "        light_idea_test_case  (1 usage found)\n" +
                 "              (1 usage found)\n" +
                 "                X.java  (1 usage found)\n" +
                 "                    1 public class X{ int xxx; } //comment\n", StringUtil.convertLineSeparators(text));
  }

  @NotNull
  private UsageViewImpl createUsageView(@NotNull Usage... usages) {
    UsageViewImpl usageView =
      (UsageViewImpl)UsageViewManager.getInstance(getProject())
        .createUsageView(UsageTarget.EMPTY_ARRAY, usages, new UsageViewPresentation(), null);
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
    DataProvider provider = new TypeSafeDataProviderAdapter((TypeSafeDataProvider)component);
    ExclusionHandler exclusionHandler = (ExclusionHandler)provider.getData(ExclusionHandler.EXCLUSION_HANDLER.getName());
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
    BinaryFileTypeDecompilers.INSTANCE.addExplicitExtension(ArchiveFileType.INSTANCE, decompiler);
    Disposer.register(getTestRootDisposable(),
                      () -> BinaryFileTypeDecompilers.INSTANCE.removeExplicitExtension(ArchiveFileType.INSTANCE, decompiler));

    PsiFile psiFile = myFixture.addFileToProject("X.jar", "xxx");
    assertEquals(ArchiveFileType.INSTANCE, psiFile.getFileType());
    assertTrue(psiFile.getFileType().isBinary());

    UsageInfo2UsageAdapter usage = new UsageInfo2UsageAdapter(new UsageInfo(psiFile));
    UsageViewImpl usageView = createUsageView(usage);

    usageView.expandAll();
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(usage.isValid());
    usage.getElement();
    usage.canNavigateToSource();
    usage.canNavigate();
    usage.getUsageInfo();
    usage.getFile();
    usage.getMergedInfos();
    usage.getPresentation();
    usage.getPlainText();
    usage.getUsageType();
    usage.getText();
    usage.getIcon();
    usage.getLocation();
    usage.getTooltipText();
    usage.getLibraryEntry();
    usage.getUsageInfo().getRangeInElement();
    usage.getUsageInfo().getElement();
    usage.getUsageInfo().getNavigationOffset();
  }
}
