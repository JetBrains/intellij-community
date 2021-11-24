// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'mapNotNullTo(){}'"
// IS_APPLICABLE_2: false
fun foo(list: List<String?>, target: MutableList<Int>) {
    <caret>for (s in list) {
        val length = s?.length ?: continue
        target.add(length)
    }
}