// "Remove getter from property" "true"

class A {
    <caret>lateinit var str: String
        get() = ""
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix