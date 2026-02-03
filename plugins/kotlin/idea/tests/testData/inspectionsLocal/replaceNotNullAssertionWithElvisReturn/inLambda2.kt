// PROBLEM: none
// WITH_STDLIB
fun test(list: List<String>, number: Int?) {
    val x: List<Int> = list.map {
        number!!<caret>
    }
}