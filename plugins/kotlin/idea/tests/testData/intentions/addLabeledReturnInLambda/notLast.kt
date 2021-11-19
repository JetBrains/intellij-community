// IS_APPLICABLE: false
// WITH_STDLIB

fun foo() {
    listOf(1,2,3).find {
        <caret>1
        true
    }
}