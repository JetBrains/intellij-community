// "Replace cast with call to 'toInt()'" "false"
// ACTION: Compiler warning 'CAST_NEVER_SUCCEEDS' options
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// WARNING: Cast can never succeed

fun foo() {
    val a = true as<caret> Int
}