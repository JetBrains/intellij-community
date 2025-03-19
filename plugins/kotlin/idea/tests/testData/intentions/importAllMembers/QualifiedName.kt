// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'java.util.regex.Pattern'"
// WITH_STDLIB

fun foo() {
    java.util.regex.Pattern<caret>.CASE_INSENSITIVE
}
