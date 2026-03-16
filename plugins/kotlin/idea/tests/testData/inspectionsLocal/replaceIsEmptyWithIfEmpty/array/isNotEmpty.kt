// PROBLEM: none
// WITH_STDLIB
fun test(arr: Array<String>): Array<String> {
    return <caret>if (arr.isNotEmpty()) {
        arr
    } else {
        arrayOf("a")
    }
}