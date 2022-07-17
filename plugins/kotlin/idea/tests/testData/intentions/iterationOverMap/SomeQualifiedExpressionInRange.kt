// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'int1' is never used
// AFTER-WARNING: Variable 'int2' is never used

fun main(args: Array<String>) {
    for (<caret>entry in 1.createListOfDataClasses()) {
        val int1 = entry.a
        val int2 = entry.b
    }
}

fun Int.createListOfDataClasses() = listOf(MyClass(this, this))

data class MyClass(val a: Int, val b: Int)
