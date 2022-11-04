package com.intellij.psi;

public final class PsiPrimitiveType extends PsiType {

  void test() {
    assert this == this;
    assert this != this;

    PsiPrimitiveType first = new PsiPrimitiveType();
    PsiPrimitiveType second = new PsiPrimitiveType();

    assert first == null;
    assert first != null;

    assert this == second;
    assert second != this;

    assert <warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">first == second</warning>;
    assert <warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">first != second</warning>;

    assert <warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">second == first</warning>;
    assert <warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">second != first</warning>;
  }
}