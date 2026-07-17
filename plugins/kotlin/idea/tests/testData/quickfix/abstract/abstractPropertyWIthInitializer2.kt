// "Remove initializer from property" "true"
// K2_ERROR: ABSTRACT_PROPERTY_WITH_INITIALIZER
abstract class A {
    abstract var i = 0<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemovePartsFromPropertyFixFactory$RemovePartsFromPropertyFix