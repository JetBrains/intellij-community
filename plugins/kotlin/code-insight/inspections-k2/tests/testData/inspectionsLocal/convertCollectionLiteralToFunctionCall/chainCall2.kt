// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
fun test() {
    val list = ["aaa", "bb", "c"].<caret>toMutableList().add("d")
}
