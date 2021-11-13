// "Convert to 'forEach'" "false"
// ACTION: Convert to anonymous function
// ACTION: Convert to single-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Move lambda argument into parentheses
// ACTION: Rename to _
// ACTION: Specify explicit lambda signature
// ACTION: Specify type explicitly
// WITH_STDLIB
fun test(list: List<String>) {
    list.forEachIndexed { index, <caret>s ->
    }
}