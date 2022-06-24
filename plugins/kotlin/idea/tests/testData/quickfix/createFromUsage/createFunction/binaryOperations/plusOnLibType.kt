// "Create extension function 'Int.plus'" "true"
// WITH_STDLIB

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2 <caret>+ A(1)
}