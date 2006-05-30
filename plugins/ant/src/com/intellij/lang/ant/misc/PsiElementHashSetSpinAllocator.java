package com.intellij.lang.ant.misc;

import com.intellij.psi.PsiElement;
import com.intellij.util.SpinAllocator;

import java.util.HashSet;

public class PsiElementHashSetSpinAllocator {

  private PsiElementHashSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<HashSet<PsiElement>> {
    public HashSet<PsiElement> createInstance() {
      return new HashSet<PsiElement>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<HashSet<PsiElement>> {
    public void disposeInstance(final HashSet<PsiElement> instance) {
      instance.clear();
    }
  }

  private static SpinAllocator<HashSet<PsiElement>> myAllocator =
    new SpinAllocator<HashSet<PsiElement>>(new Creator(), new Disposer());

  public static HashSet<PsiElement> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(HashSet<PsiElement> instance) {
    myAllocator.dispose(instance);
  }
}
