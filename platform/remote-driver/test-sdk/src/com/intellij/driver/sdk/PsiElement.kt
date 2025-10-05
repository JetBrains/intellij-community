package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.psi.PsiElement")
interface PsiElement {
  fun getProject(): Project

  fun getChildren(): Array<PsiElement>

  fun getParent(): PsiElement?

  fun getNextSibling(): PsiElement?

  fun getPrevSibling(): PsiElement?

  fun getContainingFile(): PsiFile?

  fun getText(): String?
}
