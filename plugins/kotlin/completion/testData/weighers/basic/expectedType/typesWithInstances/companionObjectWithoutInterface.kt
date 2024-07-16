package test

interface MyInterface

class MySpecialClass {
    companion object /*: Any()*/
}

val MySpecialAny = Any()

fun action(param: MyInterface) {}

fun test() {
    action(param = MySpecial<caret>)
}

// IGNORE_K2
// ORDER: MySpecialAny
// ORDER: MySpecialClass
