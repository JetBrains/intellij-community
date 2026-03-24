// "Convert extension property initializer to getter" "true"
// WITH_STDLIB
// K2_ERROR: Extension property cannot be initialized because it has no backing field.
var String.foo: Int = 0<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionPropertyInitializerToGetterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertPropertyInitializerToGetterFix