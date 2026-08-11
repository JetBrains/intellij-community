// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun coll(): Collection<Int> = emptySet<caret>()