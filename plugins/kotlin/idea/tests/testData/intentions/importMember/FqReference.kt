// INTENTION_TEXT: "Add import for 'java.util.ArrayList'"
// WITH_STDLIB

fun test() {
    val myList = java.util<caret>.ArrayList<Int>()
    val otherList = java.util.ArrayList<String>()
}

// IGNORE_K1