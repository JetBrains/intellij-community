// WITH_STDLIB
// FIX: none
// PROBLEM: Uses of 'myCustomPrintLineAlias' should probably be replaced with more robust logging

import kotlin.io.println as myCustomPrintLineAlias

fun test() {
    <caret>myCustomPrintLineAlias()
}