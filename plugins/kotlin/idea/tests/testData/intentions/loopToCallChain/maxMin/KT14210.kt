// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'filter{}.maxOrNull()'"
// INTENTION_TEXT_2: "Replace with 'asSequence().filter{}.maxOrNull()'"
// AFTER-WARNING: Variable 'result' is never used
fun f(list: List<Int>) {
    var result = -1
    <caret>for (item in list)
        if (item % 2 == 0)
            if (result <= item)
                result = item
}