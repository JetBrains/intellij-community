// DISABLE_ERRORS

fun foo(value: Int): String {
    return ("a"
            + "b"
            + <caret>(value / 10
            ) + "c")
}