// "Add interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

fun test() {
    val resolved = 1
    """$unresolved<caret> $resolved"""
}
