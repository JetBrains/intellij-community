// IGNORE_K1
// WITH_STDLIB

class A(val l: Any)

fun foo(x: A): Any = TODO()
fun bar() {
    listOf<A>().<caret>mapNotNull { value -> value as? String }
}