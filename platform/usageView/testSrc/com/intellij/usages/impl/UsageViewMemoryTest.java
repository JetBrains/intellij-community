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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ui.UIUtil;

/**
 * User: cdr
 */
public class UsageViewMemoryTest extends LightPlatformCodeInsightTestCase{
  public void testUsageViewDoesNotHoldPsiFilesOrDocuments() throws Exception {
    PsiFile psiFile = createFile("X.java", "public class X{} //iuggjhfg");
    Usage[] usages = new Usage[100];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(psiFile,i);
    }

    UsageView usageView = UsageViewManager.getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, usages, new UsageViewPresentation(), null);

    Disposer.register(getTestRootDisposable(), usageView);

    UIUtil.dispatchAllInvocationEvents();
    ((EncodingManagerImpl)EncodingManager.getInstance()).clearDocumentQueue();

    LeakHunter.checkLeak(usageView, PsiFileImpl.class);
    LeakHunter.checkLeak(usageView, Document.class);
  }

  private static Usage createUsage(PsiFile psiFile, int offset) {
    PsiElement element = psiFile.findElementAt(offset % psiFile.getTextLength());
    assertNotNull(element);
    return new UsageInfo2UsageAdapter(new UsageInfo(element));
  }
}
