// WITH_STDLIB
import kotlin.collections.List

fun test() {
    for(i in 1..10) {
        if (<warning descr="Condition 'i < 0' is always false">i < 0</warning>) {}
        if (<warning descr="Condition 'i < 1' is always false">i < 1</warning>) {}
        if (i < 2) {}
        if (<warning descr="Condition 'i >= 11' is always false">i >= 11</warning>) {}
        if (i >= 10) {}
    }
    for(i in 1 until 10) {
        if (<warning descr="Condition 'i >= 10' is always false">i >= 10</warning>) {}
    }
    for(i in 10 downTo 1) {
        if (<warning descr="Condition 'i >= 11' is always false">i >= 11</warning>) {}
        if (i >= 10) {}
        if (i >= 2) {}
        if (<warning descr="Condition 'i >= 1' is always true">i >= 1</warning>) {}
        if (<warning descr="Condition 'i >= 0' is always true">i >= 0</warning>) {}
        if (<warning descr="Condition 'i < 1' is always false">i < 1</warning>) {}
        if (i <= 1) {}
    }
}
fun testEmptyLoop(x: Int, y: Int) {
    if (y >= x) return
    for(i in <warning descr="'for' range is always empty">x..y</warning>) {
    }
    var a = 10
    for(i in 10..0) {}
    for(i in <warning descr="'for' range is always empty">a..0</warning>) {}
}
fun testArray(arr : Array<Int>) {
    for (x in arr) {
        if (<warning descr="Condition 'x > 5 && x < 3' is always false">x > 5 && <warning descr="Condition 'x < 3' is always false when reached">x < 3</warning></warning>) {}
    }
}
fun destructuring(list : List<Pair<String, Int>>) {
    var s1:String? = null
    for((s, i) in list) {
        if (i > 0) {
            s1 = s
        }
        if (s1 != null && s1 != s) {

        }
    }
}
fun destructuring2(list : List<Pair<String, Int>>) {
    var s1:String? = null
    for((s, i) in list) {
        if (i > 0) {
            s1 = s
        }
        if (<warning descr="Condition 'i > 0 && s1 != s' is always false">i > 0 && <warning descr="Condition 's1 != s' is always false when reached">s1 != s</warning></warning>) {

        }
    }
}
fun collection(list : List<String>) {
    if (list.size > 0) return
    for(x in <warning descr="'for' range is always empty">list</warning>) {

    }
}
