// AFTER-WARNING: Parameter 'a' is never used
fun foo(s: String) {
    when (s) {
        "a" -> {<caret>
            println("a") // comment 1
        }

        "b" -> {
            println("b") // comment 2
        }
    }
}

fun println(a: Any) {}