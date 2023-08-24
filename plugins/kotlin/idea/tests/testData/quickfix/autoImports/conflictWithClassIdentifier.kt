// "Import class 'Arrays'" "true"
// DISABLE-ERRORS
class java

fun test() {
    Arrays<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix