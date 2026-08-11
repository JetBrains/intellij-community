// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: someFun
// ERROR: Unresolved reference: test
// K2_AFTER_ERROR: UNRESOLVED_IMPORT
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_IMPORT
// K2_ERROR: UNRESOLVED_REFERENCE

package Teting

import Teting.test.someFun

fun main(args : Array<String>) {
    <caret>someFun
}