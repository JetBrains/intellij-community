// "Remove setter from property" "true"
// K2_ERROR: 'lateinit' modifier is not allowed on properties with a custom getter or setter.

class A {
    <caret>lateinit var str: String
        set(value) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemovePartsFromPropertyFixFactory$RemovePartsFromPropertyFix