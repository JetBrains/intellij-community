// PROBLEM: none
// WITH_STDLIB
fun test(){
    listOf(1,2,3).apply<caret> { 1.apply<caret> { forEach{ it + 1 } } }
}