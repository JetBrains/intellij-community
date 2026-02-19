// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'map{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'result' is never used
import java.util.*

fun foo(list: List<String>) {
    val result = ArrayList<Int>()

    bar()

    <caret>for (s in list) {
        result.add(s.length)
    }
}

fun bar(){}