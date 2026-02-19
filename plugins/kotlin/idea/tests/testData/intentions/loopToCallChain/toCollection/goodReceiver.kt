// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'mapTo(){}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: The value 'ArrayList<Int>()' assigned to 'var target: MutableCollection<Int> defined in foo' is never used
import java.util.ArrayList

fun foo(list: List<MutableCollection<Int>>) {
    var target: MutableCollection<Int>()
    target = ArrayList<Int>()

    <caret>for (collection in list) {
        target.add(collection.size)
    }

    if (target.size > 100) {
        target = ArrayList<Int>()
    }
}