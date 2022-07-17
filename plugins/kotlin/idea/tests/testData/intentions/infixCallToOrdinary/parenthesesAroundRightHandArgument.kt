// AFTER-WARNING: Parameter 'other' is never used
infix fun String.add(other: String) = ""

fun foo(x: String) {
    x add<caret> ("1" + "2")
}
