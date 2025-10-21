// PROBLEM: none

fun foo(input: String): String {
    return ((when {
        input.startsWith("foo") ->
            when {
                input.contains("bar") -> {
                    println("x")
                    ((if (input.endsWith("baz") || input.endsWith("qux")) {
                        if (input.isEmpty()) {
                            "a"
                        } else if (input == "b") {
                            "b"
                        } else if (input == "c")
                            ret<caret>urn "d"
                        else {
                            "e"
                        }
                    } else {
                        "f"
                    }))
                    "!!! LAST EXPRESSION !!!"
                }

                else -> "g"

            }

        else -> {
            "h"
        }
    }))
}

// IGNORE_K1
