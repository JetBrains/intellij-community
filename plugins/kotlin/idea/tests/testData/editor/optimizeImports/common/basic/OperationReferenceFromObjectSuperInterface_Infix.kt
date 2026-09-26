package test

import test.MyImpl.myInfixFun

interface MyInterface {
    infix fun Any.myInfixFun(value: Any) {}
}

object MyImpl : MyInterface

fun main() {
    "hello" myInfixFun "world"
}
