// "Import" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Unresolved reference: someFun
// ERROR: Unresolved reference: test
// K2_AFTER_ERROR: Unresolved reference 'someFun'.
// K2_AFTER_ERROR: Unresolved reference 'test'.

package Teting

import Teting.test.someFun

fun main(args : Array<String>) {
    <caret>someFun
}