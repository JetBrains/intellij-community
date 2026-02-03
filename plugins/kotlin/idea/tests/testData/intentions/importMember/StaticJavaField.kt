// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'java.util.regex.Pattern.CASE_INSENSITIVE'"
// WITH_STDLIB
// AFTER-WARNING: Variable 'v' is never used

import java.util.regex.Pattern

fun foo() {
    val v = Pattern.CASE_INSENSITIVE

    Pattern.<caret>CASE_INSENSITIVE
}
