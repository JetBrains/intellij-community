package test

import test.MyImpl.inc
import test.MyImpl.not
import test.MyImpl.plusAssign

interface MyInterface {
    operator fun Any.inc(): Any = this
    operator fun Any.not(): Any = this
    operator fun Any.plusAssign(value: Any) {}
}

object MyImpl : MyInterface

fun main() {
    var x: Any = "hello"
    x += "world"
    x++
    !x
}
