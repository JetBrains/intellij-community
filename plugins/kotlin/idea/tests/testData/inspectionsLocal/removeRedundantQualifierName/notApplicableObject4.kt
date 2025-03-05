// PROBLEM: none
// WITH_STDLIB
open class A(init: A.() -> Unit) {
    val prop: String = ""
}

object B : A({})

class D : A {
constructor(): super(
  {
      fun boo() = <caret>B.prop.toString()
  }
)
}
