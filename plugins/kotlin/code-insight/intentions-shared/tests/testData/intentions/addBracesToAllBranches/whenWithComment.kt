// AFTER-WARNING: Parameter 'a' is never used
fun println(a: Any) {}

fun foo(s: String) {
    when (s) {<caret>
        "a" ->
            // comment 1
            println("a") // comment 2

        else -> println("b")
    }
}