// WITH_STDLIB
// IS_APPLICABLE: false
// IS_APPLICABLE_2: false
fun foo(list: List<String>, target: MutableCollection<String>): String? {
    var i = 0
    <caret>for (s in list) {
        if (s.length > i) {
            target.add(s)
        }
        i++
    }
    println(i)
    return null
}