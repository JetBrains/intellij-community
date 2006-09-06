package com.intellij.lang.ant.misc;

import com.intellij.psi.PsiElement;
import com.intellij.util.SpinAllocator;

import java.util.HashSet;
import java.util.Set;

public class PsiElementSetSpinAllocator {

  private PsiElementSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<Set<PsiElement>> {
    public Set<PsiElement> createInstance() {
      return new HashSet<PsiElement>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<Set<PsiElement>> {
    public void disposeInstance(final Set<PsiElement> instance) {
      instance.clear();
    }
  }

  private static SpinAllocator<Set<PsiElement>> myAllocator =
    new SpinAllocator<Set<PsiElement>>(new Creator(), new Disposer());

  public static Set<PsiElement> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(Set<PsiElement> instance) {
    myAllocator.dispose(instance);
  }
}
