// PROBLEM: none

fun foo(input: String): String {
    return ((when {
        input.startsWith("foo") -> {
            when {
                input.contains("bar") -> {
                    println("x")
                    ((if (input.endsWith("baz") || input.endsWith("qux")) {
                        if (input.isEmpty()) {
                            "a"
                        } else if (input == "b") {
                            "b"
                        } else if (input == "c")
                            re<caret>turn "d"
                        else {
                            "e"
                        }
                    } else {
                        "f"
                    }))
                }

                else -> "g"

            }
            "!!! LAST EXPRESSION !!!"
        }

        else -> {
            "h"
        }
    }))
}

// IGNORE_K1
