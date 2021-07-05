// PROBLEM: 'for' range is always empty
// FIX: none
// WITH_RUNTIME
import kotlin.collections.List

fun test(list : List<String>) {
    if (list.size > 0) return
    for(x in <caret>list) {

    }
}
