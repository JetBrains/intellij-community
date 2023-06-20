// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages, overloadUsages
// PSI_ELEMENT_AS_TITLE: "constructor A()"
open class A(n: Int) {
  constructor<caret>() : this(1)
}

class B : A {
  constructor(n: Int) : super(n)
}

class C() : A(1)

fun test() {
  A()
  A(1)
}