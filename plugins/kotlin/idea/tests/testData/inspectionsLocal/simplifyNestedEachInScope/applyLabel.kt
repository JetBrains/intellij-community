// PROBLEM: none
// WITH_STDLIB
fun test(){
    listOf(1,2,3).apply<caret> { forEach { this@apply.contains(it) } }
}