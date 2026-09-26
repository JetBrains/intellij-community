// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none
class List {
    val size = 0
}

fun foo() {
    val list = List()
    list.size<caret> > 0
}