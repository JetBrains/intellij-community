// PROBLEM: none
// WITH_STDLIB
fun test(){
    mutableListOf(1,2,3).also<caret> { list -> list.forEach { list.add(it) } }
}