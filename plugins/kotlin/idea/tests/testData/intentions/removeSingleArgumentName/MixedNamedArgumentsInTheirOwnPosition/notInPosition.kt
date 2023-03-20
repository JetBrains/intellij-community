// COMPILER_ARGUMENTS: +XXLanguage:+MixedNamedArgumentsInTheirOwnPosition
// AFTER-WARNING: Parameter 'name1' is never used
// AFTER-WARNING: Parameter 'name2' is never used
// AFTER-WARNING: Parameter 'name3' is never used
fun foo(name1: Int, name2: Int, name3: Int) {}

fun usage() {
    foo(name2 = 2, <caret>name1 = 1, name3 = 3)
}
