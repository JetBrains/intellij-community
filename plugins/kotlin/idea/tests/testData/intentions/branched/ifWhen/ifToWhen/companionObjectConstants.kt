// WITH_STDLIB
class MyClass {
    companion object {
        const val CONST_VAL = 1
        val NON_CONST = 2
    }
}

fun test(x: Int) {
    i<caret>f (x == MyClass.CONST_VAL) {
        println("const")
    } else if (x == MyClass.NON_CONST) {
        println("non-const")
    } else {
        println("other")
    }
}
