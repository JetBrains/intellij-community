// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'sumOf{}'"
// IS_APPLICABLE_2: false
fun foo(list: List<String>): Int {
    var l = 0
    <caret>for (item in list) {
        l += item.length
    }
    return l
}
