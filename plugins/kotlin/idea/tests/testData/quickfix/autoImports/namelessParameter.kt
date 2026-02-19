// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: TTT
// K2_AFTER_ERROR: Unresolved reference 'TTT'.

fun f(: Int) {
    val t: <caret>TTT = null
}