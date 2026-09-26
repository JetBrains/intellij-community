
// PROBLEM: none
// WITH_STDLIB
fun consumeList(list: List<Int>) = println(list)

fun test(list: List<Int>) {
    list.apply<caret> {
        forEach { _ ->
            consumeList(this)
        }
    }
}
