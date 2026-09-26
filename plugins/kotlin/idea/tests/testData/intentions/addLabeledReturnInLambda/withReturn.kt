// IS_APPLICABLE: false
// WITH_STDLIB

fun foo(): Boolean  {
    listOf(1,2,3).find {
        return <caret>true
    }
    return false
}