// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(): Boolean {
    return <caret>("" ?: return false) in listOf("")
}