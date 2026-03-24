// "Implement as constructor parameters" "true"
// K2_ERROR: Class 'A' is not abstract and does not implement abstract member:<br>val foo: Int
interface I {
    val foo: Int
}

<caret>class A : I

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementAsConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementAsConstructorParameterQuickfix