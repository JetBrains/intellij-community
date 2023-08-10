// WITH_STDLIB
// AFTER-WARNING: Variable 'n' is never used

sealed class <caret>MyClass(val s: String = "") {
    fun foo() {

    }

    object FOO : MyClass("FOO")
    object BAR : MyClass("BAR")
    object DEFAULT : MyClass()
}

fun test(e: MyClass) {
    if (e == MyClass.BAR) {
        println()
    }

    val n = when (e) {
        MyClass.BAR -> 1
        MyClass.FOO -> 2
        MyClass.DEFAULT -> 0
    }
}