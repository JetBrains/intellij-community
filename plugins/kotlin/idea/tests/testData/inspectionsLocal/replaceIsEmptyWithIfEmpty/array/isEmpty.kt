// PROBLEM: none
// WITH_STDLIB
fun test(arr: Array<String>): Array<String> {
    return <caret>if (arr.isEmpty()) {
        arrayOf("a")
    } else {
        arr
    }
}