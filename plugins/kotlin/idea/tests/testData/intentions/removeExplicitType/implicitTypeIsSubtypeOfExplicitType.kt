// AFTER-WARNING: Variable 'a' is never used
open class A<T>

class F<T> : A<T>()

fun test() {
    val a: <caret>A<String> = F<String>()
}