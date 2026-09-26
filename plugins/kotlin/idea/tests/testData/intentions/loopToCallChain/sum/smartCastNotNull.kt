// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'filterNotNull().sum()'"
// INTENTION_TEXT_2: "Replace with 'asSequence().filterNotNull().sum()'"
// AFTER-WARNING: Variable 'result' is never used
fun foo(list: List<Int?>){
    var result = 0
    <caret>for (l in list)
        if (l != null)
            result += l
}