// WITH_STDLIB
// IS_APPLICABLE: false

fun test() {
    val myList = java.util<caret>.ArrayList<Int>()
    val otherList = java.util.ArrayList<String>()
}
