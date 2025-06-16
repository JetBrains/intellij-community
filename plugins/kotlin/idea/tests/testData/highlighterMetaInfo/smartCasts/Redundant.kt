// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// WITH_STDLIB
fun test(any: Any) {
    if (any is String) return
    println(any)
}

fun Any.test2() {
    if (this is String) return
    foo()
}

fun Any.foo() {}
