package Jet86

class A {
  companion object {
    val x = 1
  }
  <error descr="[MANY_COMPANION_OBJECTS]">companion</error> object Another { // error
    val x = 1
  }
}

class B() {
  val x = 12
}

object b {
  <error descr="[WRONG_MODIFIER_CONTAINING_DECLARATION]">companion</error> object {
    val x = 1
  }
  // error
}

val a = A.x
val c = B.<error>x</error>
val d = b.<error>x</error>

val s = <error>System</error>  // error
fun test() {
  System.out.println()
  java.lang.System.out.println()
}