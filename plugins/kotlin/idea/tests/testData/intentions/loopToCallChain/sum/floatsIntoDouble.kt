// API_VERSION: 1.3
// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'sumByDouble{}'"
// IS_APPLICABLE_2: false
fun foo(list: List<Float>): Double {
    var s = 0.0
    <caret>for (item in list) {
        s += item
    }
    return s
}
