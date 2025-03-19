package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.psi.PsiManager")
interface PsiManager {
  fun findFile(file: VirtualFile): PsiFile?
}
