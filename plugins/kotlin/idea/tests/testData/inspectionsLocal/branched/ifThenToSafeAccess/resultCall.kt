// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
// DISABLE_ERRORS
// WITH_STDLIB

val someNullableString: String? = ""
fun String.bar(): Result<String> = Result.success("")
val result = if<caret> (someNullableString == null) {
    null
} else {
    someNullableString.bar()
}