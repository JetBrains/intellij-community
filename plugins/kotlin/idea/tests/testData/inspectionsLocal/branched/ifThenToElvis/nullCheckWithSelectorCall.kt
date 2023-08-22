// WITH_STDLIB

val nullableString: String? = "abc"

val foo = if (<caret>nullableString != null) {
    nullableString.toUpperCase()
} else {
    ""
}