// "Convert extension property initializer to getter" "true"
var String.foo: Int = 0<caret>
    set(value) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionPropertyInitializerToGetterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertPropertyInitializerToGetterFix