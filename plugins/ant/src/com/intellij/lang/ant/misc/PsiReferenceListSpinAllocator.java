package com.intellij.lang.ant.misc;

import com.intellij.psi.PsiReference;
import com.intellij.util.SpinAllocator;

import java.util.ArrayList;
import java.util.List;

public class PsiReferenceListSpinAllocator {

  private PsiReferenceListSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<List<PsiReference>> {
    public List<PsiReference> createInstance() {
      return new ArrayList<PsiReference>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<List<PsiReference>> {
    public void disposeInstance(final List<PsiReference> instance) {
      instance.clear();
    }
  }

  private static SpinAllocator<List<PsiReference>> myAllocator =
    new SpinAllocator<List<PsiReference>>(new Creator(), new Disposer());

  public static List<PsiReference> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(List<PsiReference> instance) {
    myAllocator.dispose(instance);
  }
}
