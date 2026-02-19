// INTENTION_TEXT: "Add import for 'java.util.regex.Pattern.CASE_INSENSITIVE'"
// WITH_STDLIB

import java.util.regex.Pattern

fun foo() {
    <caret>Pattern.CASE_INSENSITIVE
}

// IGNORE_K1