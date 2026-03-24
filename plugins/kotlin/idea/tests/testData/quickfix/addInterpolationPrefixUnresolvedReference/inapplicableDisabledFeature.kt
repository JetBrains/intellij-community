// "Add interpolation prefix" "false"
// LANGUAGE_VERSION: 2.0
// K2_ERROR: Unresolved reference 'unresolved'.
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

fun test() {
    """$unresolved<caret>"""
}
