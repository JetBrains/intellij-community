// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

// test 1
interface A
abstract class B : A
abstract class C : B()

fun test(a: A) {}

fun usage(b: B) {
    if (b is C) {
        test(b)
    }
}

// test 2
fun foo(arg: Any?): Any? {
    return if (arg != null) arg else null
}