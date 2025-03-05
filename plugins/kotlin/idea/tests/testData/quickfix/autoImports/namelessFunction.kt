// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: TTTTT
// ERROR: Function declaration must have a name
// K2_AFTER_ERROR: Function declaration must have a name.
// K2_AFTER_ERROR: Unresolved reference 'TTTTT'.

fun () {
    val tttt : <caret>TTTTT = null
}
