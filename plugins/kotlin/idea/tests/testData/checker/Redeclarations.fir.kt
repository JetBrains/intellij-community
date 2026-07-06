//package redeclarations {
  <error descr="[REDECLARATION]">object A</error> {
    val x : Int = 0

    val A = 1
  }

  <error descr="[REDECLARATION]">class A {}</error>

  <error descr="[REDECLARATION]">val A = 1</error>

//}
