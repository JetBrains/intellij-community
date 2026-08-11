// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: infix
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package x

object infix {
    fun invoke() {

    }
}

fun x() {
    "" <caret>infix ""
}