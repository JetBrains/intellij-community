// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
class A {
    val property: String? = "A"
}

fun m(a: A, b: A) {
    <caret>if (a.property == "a") {
        "a"
    } else if (b.property == "b") {
        "b"
    }
}