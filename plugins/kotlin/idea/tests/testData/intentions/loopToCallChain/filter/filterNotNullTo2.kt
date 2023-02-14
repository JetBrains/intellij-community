// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'filterNotNullTo()'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'target' is never used
import java.util.ArrayList

fun foo(list: List<String?>) {
    val target = ArrayList<String>(1000)
    <caret>for (s in list) {
        if (s != null) {
            target.add(s)
        }
    }
}