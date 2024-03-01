fun abc() {
    if (a == b || a != b && c < d && !(c > 3)) {}
    5 in 3
    5 !in 3
    val a = <caret>
}

// ABSENT: ==, ||, &&, ||, in, !in, !