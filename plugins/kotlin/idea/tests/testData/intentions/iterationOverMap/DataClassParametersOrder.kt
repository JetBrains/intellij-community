// WITH_RUNTIME

fun main(args: Array<String>) {
    val list = listOf(MyClass(1, 2, 3), MyClass(2, 3, 4))
    for (<caret>klass in list) {
        val a1 = klass.a
        val c1 = klass.c
        val b1 = klass.b
    }
}

data class MyClass(val a: Int, val b: Int, val c: Int)