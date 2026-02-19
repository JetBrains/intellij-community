// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'java.util.regex.Pattern.matches'"
// WITH_STDLIB
// ERROR: Unresolved reference: SomethingElse
// ERROR: Unresolved reference: somethingElse
// SKIP_ERRORS_AFTER
// K2_ERROR: Unresolved reference 'SomethingElse'.
// K2_ERROR: Unresolved reference 'somethingElse'.

import java.util.regex.Pattern

fun foo() {
    Pattern.<caret>matches("", "")
}

fun bar() {
    Pattern.matches("", "")

    java.util.regex.Pattern.matches("", "")

    Pattern.compile("")

    SomethingElse.matches("", "")

    somethingElse.Pattern.matches("", "")
}