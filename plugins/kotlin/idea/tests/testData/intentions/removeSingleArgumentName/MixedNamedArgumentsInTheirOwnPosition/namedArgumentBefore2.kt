// COMPILER_ARGUMENTS: -XXLanguage:+MixedNamedArgumentsInTheirOwnPosition
// AFTER-WARNING: Parameter 'name1' is never used
// AFTER-WARNING: Parameter 'name2' is never used
// AFTER-WARNING: Parameter 'name3' is never used
fun foo(name1: Int, name2: Int, name3: Int) {}

fun usage() {
    foo(1, name2 = 2, <caret>name3 = 3)
}