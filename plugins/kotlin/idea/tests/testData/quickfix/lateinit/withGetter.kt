// "Remove getter from property" "true"

class A {
    <caret>lateinit var str: String
        get() = ""
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix