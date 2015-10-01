/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.testFramework;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;

/**
 * @author peter
 */
public abstract class LiteFixture extends PlatformLiteFixture {
  public static void setContext(final PsiFile psiFile, final PsiElement context) {
    if (context != null) {
      psiFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, SmartPointerManager.getInstance(context.getProject()).createSmartPsiElementPointer(context));
    }
  }
}
