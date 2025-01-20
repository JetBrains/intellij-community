// IS_APPLICABLE: false
// WITH_STDLIB
// ERROR: Unresolved reference: unresolved
// K2_ERROR: Unresolved reference 'unresolved'.

import java.util.regex.Pattern

fun foo() {
    Pattern.<caret>unresolved()
}
