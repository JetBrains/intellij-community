package com.intellij.psi

abstract class PsiType {

  companion object {
    //@JvmField
    val VOID = PsiPrimitiveType()
    //@JvmField
    val NULL = PsiPrimitiveType()

    //@JvmField
    val EMPTY_ARRAY = arrayOf<PsiType>()
  }

  open fun test() {
    if (EMPTY_ARRAY === EMPTY_ARRAY) {}
    //if (VOID !== NULL) {} // TODO add @JvmField
  }
}

class PsiPrimitiveType : PsiType() {

  override fun test() {
    if (this === this) {}
    if (this !== this) {}

    val first: PsiPrimitiveType = PsiPrimitiveType()
    val second: PsiPrimitiveType? = PsiPrimitiveType()

    if (this === second) {}
    if (second !== this) {}

    if (<warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">first === second</warning>) {}
    if (<warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">first !== second</warning>) {}

    if (<warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">second === first</warning>) {}
    if (<warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">second !== first</warning>) {}

    val third: MyPsiPrimitiveType = first

    if (<warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">third === first</warning>) {}
    if (<warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">third !== first</warning>) {}
  }
}

typealias MyPsiPrimitiveType = PsiPrimitiveType