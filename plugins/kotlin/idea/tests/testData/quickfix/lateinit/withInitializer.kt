// "Remove initializer from property" "true"

class A {
    <caret>lateinit var str = ""
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix