// PROBLEM: none
// WITH_STDLIB

fun String.test(): Int {
    return let<caret> {
        it.length
    }
}