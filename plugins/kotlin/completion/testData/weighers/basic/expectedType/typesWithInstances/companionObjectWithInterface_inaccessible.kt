package test

interface MyInterface

class MySpecialClass1 {
    private companion object : MyInterface
}

open class MySpecialClass2 {
    protected companion object : MyInterface
}

val MySpecialAny = Any()

fun action(param: MyInterface) {}

fun test() {
    action(param = MySpecial<caret>)
}

// IGNORE_K2
// ORDER: MySpecialAny
// ORDER: MySpecialClass1
// ORDER: MySpecialClass2
