// "Convert 'Int.() -> Int' to '(Int) -> Int'" "true"
// IGNORE_K2

expect fun foo(n: Int, action: <caret>Int.() -> Int): Int

fun test() {
    foo(1) { this + 1 }
}