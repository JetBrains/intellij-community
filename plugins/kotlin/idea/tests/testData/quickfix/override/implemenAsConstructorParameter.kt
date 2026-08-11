// "Implement as constructor parameters" "true"
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
interface I {
    val foo: Int
}

<caret>class A : I

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementAsConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementAsConstructorParameterQuickfix