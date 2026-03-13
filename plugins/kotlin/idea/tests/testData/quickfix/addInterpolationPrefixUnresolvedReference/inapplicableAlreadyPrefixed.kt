// "Add interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_ERROR: Unresolved reference 'unresolved'.
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

fun test() {
    $$"""$$unresolved<caret>"""
}
