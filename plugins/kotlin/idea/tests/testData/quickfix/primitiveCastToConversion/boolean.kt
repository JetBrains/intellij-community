// "Replace cast with call to 'toInt()'" "false"
// ACTION: Compiler warning 'CAST_NEVER_SUCCEEDS' options
// WARNING: Cast can never succeed

fun foo() {
    val a = true as<caret> Int
}