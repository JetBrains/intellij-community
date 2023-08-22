// WITH_STDLIB
// IS_APPLICABLE: false
// IS_APPLICABLE_2: false
fun foo(list: List<String>) {
    <caret>for ((index, s) in list.withIndex()) {
        println(s.hashCode() * index)
    }
}