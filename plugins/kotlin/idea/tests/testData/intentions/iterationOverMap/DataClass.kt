// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'a' is never used
// AFTER-WARNING: Variable 'b' is never used
// AFTER-WARNING: Variable 'c' is never used

fun main(args: Array<String>) {
    val list = listOf(MyClass(1, 2, 3), MyClass(2, 3, 4))
    for (<caret>klass in list) {
        val a = klass.a
        val b = klass.b
        val c = klass.c
    }
}

data class MyClass(val a: Int, val b: Int, val c: Int)