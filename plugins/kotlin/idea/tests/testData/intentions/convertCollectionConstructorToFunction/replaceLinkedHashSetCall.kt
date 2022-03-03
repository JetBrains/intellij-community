// WITH_STDLIB
// AFTER-WARNING: Variable 'list' is never used

import java.util.LinkedHashSet

fun foo() {
    var list: LinkedHashSet<Int> = <caret>LinkedHashSet()
}