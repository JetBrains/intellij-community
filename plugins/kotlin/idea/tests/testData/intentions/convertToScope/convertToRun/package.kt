// IS_APPLICABLE: false
// WITH_STDLIB
fun test(s: String?) {
    <caret>kotlin.checkNotNull(s)
}
