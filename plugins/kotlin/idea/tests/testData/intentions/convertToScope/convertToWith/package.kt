// IS_APPLICABLE: false
// WITH_RUNTIME
fun test(s: String?) {
    <caret>kotlin.checkNotNull(s)
}
