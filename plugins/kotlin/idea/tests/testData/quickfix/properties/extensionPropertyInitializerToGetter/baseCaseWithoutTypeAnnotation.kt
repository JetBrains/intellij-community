// "Convert extension property initializer to getter" "true"
val String.foo = 0<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionPropertyInitializerToGetterFix