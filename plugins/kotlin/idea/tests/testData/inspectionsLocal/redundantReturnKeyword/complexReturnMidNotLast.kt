// PROBLEM: none

fun foo(input: String): String {
    ((when {
        input.startsWith("foo") ->
            when {
                input.contains("bar") -> {
                    println("x")
                    return ((if (input.endsWith("baz") || input.endsWith("qux")) {
                        if (input.isEmpty()) {
                            "a"
                        } else if (input == "b") {
                            "b"
                        } else if (input == "c")
                            ret<caret>urn "d"
                        else {
                            "e"
                        }
                        "!!! LAST EXPRESSION !!!"
                    } else {
                        "f"
                    }))
                }

                else -> "g"
            }

        else -> {
            "h"
        }
    }))
    return "u"
}

// IGNORE_K1
