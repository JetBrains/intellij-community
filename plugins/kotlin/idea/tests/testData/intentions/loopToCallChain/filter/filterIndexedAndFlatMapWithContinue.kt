// WITH_STDLIB
// INTENTION_TEXT: "Replace with '...flatMap{}.filter{}.mapTo(){}'"
// INTENTION_TEXT_2: "Replace with 'asSequence()...flatMap{}.filter{}.mapTo(){}'"
// AFTER-WARNING: Parameter 's' is never used, could be renamed to _
fun foo(list: List<String>, target: MutableCollection<String>) {
    var i = 0
    <caret>for (s in list) {
        if (i % 10 != 0) {
            for (j in s.indices) {
                if (j == 10) continue
                target.add(j.toString())
            }
        }
        i++
    }
}