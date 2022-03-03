// PROBLEM: none
// WITH_STDLIB
fun test(){
    listOf(1,2,3).also<caret> { listOf(1,2,3).forEach { } }
}