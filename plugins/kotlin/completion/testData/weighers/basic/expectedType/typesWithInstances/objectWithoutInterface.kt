package test

interface MyInterface

object MySpecialObject /*: Any()*/

val MySpecialAny = Any()

fun action(param: MyInterface) {}

fun test() {
    action(param = MySpecial<caret>)
}

// IGNORE_K2
// ORDER: MySpecialAny
// ORDER: MySpecialObject
