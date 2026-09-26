// WITH_STDLIB
fun test(x: Any?) {
    i<caret>f (x == null) {
        println("null")
    } else if (x is String) {
        println("string: $x")
    } else if (x is Int) {
        println("int: $x")
    } else {
        println("other")
    }
}
