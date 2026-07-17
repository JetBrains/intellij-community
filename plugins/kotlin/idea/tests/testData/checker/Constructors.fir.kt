// EXPECTED_DUPLICATED_HIGHLIGHTING
open class NoC
class NoC1 : NoC()

class WithC0() : NoC()
open class WithC1() : NoC()
class NoC2 : <error descr="[SUPERTYPE_NOT_INITIALIZED]">WithC1</error>
class NoC3 : WithC1()
class WithC2() : <error descr="[SUPERTYPE_NOT_INITIALIZED]">WithC1</error>

class NoPC {
}

class WithPC0() {
}

class WithPC1(a : Int) {
}


class Foo() : <error descr="[FINAL_SUPERTYPE]">WithPC0</error>(), <error descr="Type expected"><error descr="[SYNTAX]"><error descr="[SYNTAX]">this</error></error></error>() {

}

class WithCPI_Dup(x : Int) {
  <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">var x : Int</error>
}

class WithCPI(x : Int) {
  val a = 1
  val xy : Int = x
}

class NoCPI {
  val a = 1
  var ab = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">1</error>
    get() = 1
    set(v) {}
}
