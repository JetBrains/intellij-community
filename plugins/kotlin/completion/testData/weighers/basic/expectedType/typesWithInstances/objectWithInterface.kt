package test

interface MyInterface

object MySpecialObject : MyInterface

val MySpecialAny = Any()

fun action(param: MyInterface) {}

fun test() {
    action(param = MySpecial<caret>)
}

// ORDER: MySpecialObject
// ORDER: MySpecialAny
