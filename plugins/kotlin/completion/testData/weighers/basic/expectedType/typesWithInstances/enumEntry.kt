package test

import test.MyEnum.MY_ENTRY

interface MyInterface

enum class MyEnum : MyInterface {
    MY_ENTRY
}

val myAny = Any()

fun action(param: MyInterface) {}

fun test() {
    action(param = <caret>)
}

// IGNORE_K2
// ORDER: MY_ENTRY
// ORDER: myAny
