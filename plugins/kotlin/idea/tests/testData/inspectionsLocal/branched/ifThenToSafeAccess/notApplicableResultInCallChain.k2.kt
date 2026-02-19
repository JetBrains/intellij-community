/* In K2 the behavior is different, because limitations regarding `Result` are lifted since Kotlin 1.5 */
/* See https://github.com/Kotlin/KEEP/blob/master/proposals/stdlib/result.md */
// FIX: Replace 'if' expression with safe access expression
// WITH_STDLIB
// DISABLE_ERRORS

val someNullableString: String? = ""
fun String.bar(): Result<String> = Result.success("")
val result = if<caret> (someNullableString == null) {
    null
} else {
    someNullableString.bar().getOrNull().let { }
}