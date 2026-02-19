// PROBLEM: none
// WITH_STDLIB
open class A {
   open fun foo(number: Int) {
      print(number)
   }
}

class B : A() {
   override fun foo(n<caret>umber: Int) {
      print("my")
   }
}