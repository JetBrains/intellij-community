/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ui.UIUtil;

/**
 * User: cdr
 */
public class UsageViewTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testUsageViewDoesNotHoldPsiFilesOrDocuments() throws Exception {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{} //iuggjhfg");
    Usage[] usages = new Usage[100];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(psiFile,i);
    }

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, usages, new UsageViewPresentation(), null);

    Disposer.register(getTestRootDisposable(), usageView);

    ((EncodingManagerImpl)EncodingManager.getInstance()).clearDocumentQueue();
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    LeakHunter.checkLeak(usageView, PsiFileImpl.class, PsiFileImpl::isPhysical);
    LeakHunter.checkLeak(usageView, Document.class);
  }

  public void testUsageViewHandlesDocumentChange() throws Exception {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{ int xxx; } //comment");
    Usage usage = createUsage(psiFile, psiFile.getText().indexOf("xxx"));

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, new Usage[]{usage}, new UsageViewPresentation(), null);
    Disposer.register(getTestRootDisposable(), usageView);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    document.insertString(0, "/* sdfsdfsd */");
    documentManager.commitAllDocuments();
    int navigationOffset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
    assertEquals(psiFile.getText().indexOf("xxx"), navigationOffset);
  }
  public void testTextUsageInfoHandlesDocumentChange() throws Exception {
    PsiFile psiFile = myFixture.addFileToProject("X.java", "public class X{ int xxx; } //comment");
    Usage usage = new UsageInfo2UsageAdapter(new UsageInfo(psiFile, psiFile.getText().indexOf("xxx"), StringUtil.indexOfSubstringEnd(psiFile.getText(),"xxx")));

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, new Usage[]{usage}, new UsageViewPresentation(), null);
    Disposer.register(getTestRootDisposable(), usageView);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    document.insertString(0, "/* sdfsdfsd */");
    documentManager.commitAllDocuments();
    int navigationOffset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
    assertEquals(psiFile.getText().indexOf("xxx"), navigationOffset);
  }

  private static Usage createUsage(PsiFile psiFile, int offset) {
    PsiElement element = psiFile.findElementAt(offset % psiFile.getTextLength());
    assertNotNull(element);
    return new UsageInfo2UsageAdapter(new UsageInfo(element));
  }
}
