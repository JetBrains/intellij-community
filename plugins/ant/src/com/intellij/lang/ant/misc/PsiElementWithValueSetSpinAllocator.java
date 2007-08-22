package com.intellij.lang.ant.misc;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.SpinAllocator;

import java.util.HashSet;
import java.util.Set;

public class PsiElementWithValueSetSpinAllocator {

  private PsiElementWithValueSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<Set<Pair<PsiElement, String>>> {
    public Set<Pair<PsiElement, String>> createInstance() {
      return new HashSet<Pair<PsiElement, String>>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<Set<Pair<PsiElement, String>>> {
    public void disposeInstance(final Set<Pair<PsiElement, String>> instance) {
      instance.clear();
    }
  }

  private static SpinAllocator<Set<Pair<PsiElement, String>>> myAllocator =
    new SpinAllocator<Set<Pair<PsiElement, String>>>(new Creator(), new Disposer());

  public static Set<Pair<PsiElement, String>> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(Set<Pair<PsiElement, String>> instance) {
    myAllocator.dispose(instance);
  }
}