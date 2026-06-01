// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call and remove type conversion
fun test() {
    val list = ["aaa", "bb", "c"].<caret>toMutableList().add("d")
}
