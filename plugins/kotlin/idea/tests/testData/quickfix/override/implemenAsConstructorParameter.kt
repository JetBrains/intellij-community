// "Implement as constructor parameters" "true"
interface I {
    val foo: Int
}

<caret>class A : I

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementAsConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementAsConstructorParameterQuickfix