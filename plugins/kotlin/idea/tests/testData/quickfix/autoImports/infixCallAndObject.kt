// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: infix
// K2_ERROR: Unresolved reference 'infix' on receiver of type 'String'.
// K2_AFTER_ERROR: Unresolved reference 'infix' on receiver of type 'String'.
package x

object infix {
    fun invoke() {

    }
}

fun x() {
    "" <caret>infix ""
}