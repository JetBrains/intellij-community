// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun main() {
    buildMap {
        getOrPut("key") { mutable<caret>ListOf() }.add(42)
    }
}
