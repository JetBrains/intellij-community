package com.intellij.psi;

public abstract class PsiType {

  public static final PsiPrimitiveType VOID = new PsiPrimitiveType();
  public static final PsiPrimitiveType NULL = new PsiPrimitiveType();

  public static final PsiType[] EMPTY_ARRAY = {};

  void test() {
    assert EMPTY_ARRAY == EMPTY_ARRAY;
    assert VOID != NULL;
  }
}