// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: TTT
// K2_AFTER_ERROR: Unresolved reference 'TTT'.

val : Int
    get() {
        val t : <caret>TTT = null
        return 1
    }