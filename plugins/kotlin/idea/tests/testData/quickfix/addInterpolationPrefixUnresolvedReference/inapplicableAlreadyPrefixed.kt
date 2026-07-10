// "Add interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE

fun test() {
    $$"""$$unresolved<caret>"""
}
