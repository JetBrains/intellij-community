// PROBLEM: none

data object O

fun foo(arg: Any) {
    if (arg <caret>!is O) {
    }
}

// IGNORE_K1