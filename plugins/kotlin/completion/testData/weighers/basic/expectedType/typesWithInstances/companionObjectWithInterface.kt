package test

interface MyInterface

class MySpecialClass1 {
    companion object : MyInterface
}

class MySpecialClass2 {
    internal companion object : MyInterface
}

private class MySpecialClass3 {
    companion object : MyInterface
}


val MySpecialAny = Any()

fun action(param: MyInterface) {}

fun test() {
    action(param = MySpecial<caret>)
}

// ORDER: MySpecialClass1
// ORDER: MySpecialClass2
// ORDER: MySpecialClass3
// ORDER: MySpecialAny
