/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

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

    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{} //iuggjhfg");
    Usage[] usages = new Usage[100];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(psiFile,i);
    }

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, usages, new UsageViewPresentation(), null);

    Disposer.register(myFixture.getTestRootDisposable(), usageView);

    ((EncodingManagerImpl)EncodingManager.getInstance()).clearDocumentQueue();
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    LeakHunter.checkLeak(usageView, PsiFileImpl.class, PsiFileImpl::isPhysical);
    LeakHunter.checkLeak(usageView, Document.class);
  }

  public void testUsageViewHandlesDocumentChange() {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{ int xxx; } //comment");
    Usage usage = createUsage(psiFile, psiFile.getText().indexOf("xxx"));

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, new Usage[]{usage}, new UsageViewPresentation(), null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "/* sdfsdfsd */"));
    documentManager.commitAllDocuments();
    int navigationOffset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
    assertEquals(psiFile.getText().indexOf("xxx"), navigationOffset);
  }
  public void testTextUsageInfoHandlesDocumentChange() {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{ int xxx; } //comment");
    Usage usage = new UsageInfo2UsageAdapter(new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(),"xxx")));

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, new Usage[]{usage}, new UsageViewPresentation(), null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "/* sdfsdfsd */"));
    documentManager.commitAllDocuments();
    int navigationOffset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
    assertEquals(psiFile.getText().indexOf("xxx"), navigationOffset);
  }

  private static Usage createUsage(PsiFile psiFile, int offset) {
    PsiElement element = psiFile.findElementAt(offset % psiFile.getTextLength());
    assertNotNull(element);
    return new UsageInfo2UsageAdapter(new UsageInfo(element));
  }

  public void testUsageViewCanRerunAfterTargetWasInvalidatedAndRestored() {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{" +
                                                           "    void foo() {\n" +
                                                           "        bar();\n" +
                                                           "        bar();\n" +
                                                           "    }" +
                                                           "    void bar() {}\n" +
                                                           "}");
    Usage usage = createUsage(psiFile, psiFile.getText().indexOf("bar();"));

    PsiElement[] members = psiFile.getChildren()[psiFile.getChildren().length - 1].getChildren();
    PsiNamedElement bar = (PsiNamedElement)members[members.length - 3];
    assertEquals("bar", bar.getName());

    UsageTarget target = new PsiElement2UsageTargetAdapter(bar);
    FindUsagesManager usagesManager = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager();
    FindUsagesHandler handler = usagesManager.getNewFindUsagesHandler(bar, false);
    UsageViewImpl usageView =
      (UsageViewImpl)usagesManager.doFindUsages(new PsiElement[]{bar}, PsiElement.EMPTY_ARRAY, handler, handler.getFindUsagesOptions(), false);

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

  public void testExcludeUsageMustExcludeChildrenAndParents() {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{ int xxx; } //comment");
    Usage usage = new UsageInfo2UsageAdapter(new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(),"xxx")));

    UsageViewImpl usageView =
      (UsageViewImpl)UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, new Usage[]{usage}, new UsageViewPresentation(), null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);

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

  public void testExcludeNodeMustExcludeChildrenAndParents() {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{ int xxx; } //comment");
    Usage usage = new UsageInfo2UsageAdapter(new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(),"xxx")));

    UsageViewImpl usageView =
      (UsageViewImpl)UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, new Usage[]{usage}, new UsageViewPresentation(), null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);
    UIUtil.dispatchAllInvocationEvents();

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
}
