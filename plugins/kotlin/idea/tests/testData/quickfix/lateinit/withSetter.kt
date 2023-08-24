// "Remove setter from property" "true"

class A {
    <caret>lateinit var str: String
        set(value) {}
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix