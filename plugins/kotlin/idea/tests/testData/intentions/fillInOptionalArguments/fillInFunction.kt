// WITH_STDLIB
// AFTER-WARNING: Unreachable code
fun foo(a: String, b: Int = 0, c: String) {
    println("$a$b$c")
}

fun bar() {
    foo(
        a = TODO(),
        c = TODO(),<caret>
    )
}
