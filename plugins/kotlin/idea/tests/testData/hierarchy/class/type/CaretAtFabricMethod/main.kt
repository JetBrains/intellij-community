package a

fun foo() {
  A<caret>()
}

class A (x: Int)

fun A(): a.A = A(1)


