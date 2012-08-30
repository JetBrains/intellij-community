package org.jetbrains.android.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.TestOnly;

/**
* @author Eugene.Kudelevsky
*/
class AndroidInlineTestConfig {
  private final boolean myInlineThisOnly;
  private MultiMap<PsiElement, String> myConflicts = null;

  @TestOnly
  AndroidInlineTestConfig(boolean inlineThisOnly) {
    myInlineThisOnly = inlineThisOnly;
  }

  public boolean isInlineThisOnly() {
    return myInlineThisOnly;
  }

  public void setConflicts(MultiMap<PsiElement, String> conflicts) {
    myConflicts = conflicts;
  }

  @TestOnly
  public MultiMap<PsiElement, String> getConflicts() {
    return myConflicts;
  }
}
