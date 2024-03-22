// PROBLEM: none
// WITH_STDLIB

fun withAssign(arg: String?): String {
    var result: String = ""
    arg?.let<caret> { result = it }
    return result
}