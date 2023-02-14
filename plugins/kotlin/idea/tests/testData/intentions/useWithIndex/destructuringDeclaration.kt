// WITH_STDLIB
// IS_APPLICABLE: false
// AFTER-WARNING: Variable 's1' is never used
// AFTER-WARNING: Variable 's2' is never used
fun foo(list: List<Pair<String, String>>) {
    var index = 0
    <caret>for ((s1, s2) in list) {
        index++
    }
}