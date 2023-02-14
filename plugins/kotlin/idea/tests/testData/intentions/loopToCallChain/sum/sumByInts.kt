// API_VERSION: 1.3
// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'sumBy{}'"
// IS_APPLICABLE_2: false
fun foo(list: List<String>): Int {
    var l = 0
    <caret>for (item in list) {
        l += item.length
    }
    return l
}
