package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

/**
 * Reference to element have been matched
 */
public class SmartPsiPointer {
  private SmartPsiElementPointer pointer;

  public SmartPsiPointer(PsiElement element) {
    pointer = element != null ? SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element):null;
  }

  public VirtualFile getFile() {
    return pointer != null ? pointer.getVirtualFile():null;
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
    return pointer != null ? pointer.getElement():null;
  }

  public void clear() {
    pointer = null;
  }

  public Project getProject() {
    PsiElement element = getElement();
    return element == null ? null : element.getProject();
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
