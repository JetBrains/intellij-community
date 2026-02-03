class Test {
    val fooVal: Int = 5
    fun fooFun(): Int = 5
}

fun main() {
    foo<caret>
}

// INVOCATION_COUNT: 2
// ABSENT: fooVal
// ABSENT: fooFun