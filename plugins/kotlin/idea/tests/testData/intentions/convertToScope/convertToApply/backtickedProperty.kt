// WITH_STDLIB

class MyClass {
    fun foo() = Unit
    fun bar() = Unit
    fun baz() = Unit
}

fun test() {
    val `when` = MyClass()
    `when`.foo()<caret>
    `when`.bar()
    `when`.baz()
}
