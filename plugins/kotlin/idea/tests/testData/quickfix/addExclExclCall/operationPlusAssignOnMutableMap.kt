// "Add non-null asserted (!!) call" "true"
// WITH_STDLIB
fun test(map: MutableMap<Int, Int>) {
    map[3] +=<caret> 5
}
