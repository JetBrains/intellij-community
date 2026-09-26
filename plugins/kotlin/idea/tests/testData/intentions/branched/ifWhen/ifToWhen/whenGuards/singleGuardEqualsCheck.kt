// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(i: Int, b: Boolean) {
    if<caret> (i == 0 && b) {
        println("Foo")
    } else if (i == 7 && !b) {
        println("Bar")
    } else {
        println("Else")
    }
}
