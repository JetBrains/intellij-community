// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: infix
// K2_AFTER_ERROR: Unresolved reference 'infix'.
package x

object infix {
    fun invoke() {

    }
}

fun x() {
    "" <caret>infix ""
}