package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

/**
 * Reference to element have been matched
 */
public class SmartPsiPointer {
  @NotNull 
  private final SmartPsiElementPointer<?> pointer;

  public SmartPsiPointer(@NotNull PsiElement element) {
    pointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public VirtualFile getFile() {
    return pointer.getVirtualFile();
  }

  public int getOffset() {
    PsiElement element = getElement();
    return element == null ? -1 : element.getTextRange().getStartOffset();
  }

  public int getLength() {
    PsiElement element = getElement();
    return element == null ? 0 : element.getTextRange().getEndOffset();
  }

  public PsiElement getElement() {
    return pointer.getElement();
  }

  public boolean equals(Object o) {
    if (!(o instanceof SmartPsiPointer)) {
      return false;
    }
    final SmartPsiPointer ref = (SmartPsiPointer)o;
    return ref.pointer.equals(pointer);
  }

  public int hashCode() {
    PsiElement element = getElement();
    return element == null ? 0 : element.hashCode();
  }
}
