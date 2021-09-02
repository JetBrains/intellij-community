// PROBLEM: none
// WITH_RUNTIME
fun test(list : List<Pair<String, Int>>) {
    var s1:String? = null
    for((s, i) in list) {
        if (i > 0) {
            s1 = s
        }
        if (s1 != null && <caret>s1 != s) {

        }
    }
}
