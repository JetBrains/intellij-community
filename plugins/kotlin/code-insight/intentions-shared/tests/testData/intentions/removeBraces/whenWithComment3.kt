// AFTER-WARNING: Parameter 'a' is never used
fun foo(s: String) {
    when (s) {
        "a" -> {<caret>
            println("a") // 1
            // 2
            // 3
        }

        "b" -> {
            println("b")
        }
    }
}

fun println(a: Any) {}