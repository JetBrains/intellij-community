// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'java.util.Objects'"
// WITH_STDLIB

fun foo() {
    <caret>java.util.Objects.equals(null, null)
}
