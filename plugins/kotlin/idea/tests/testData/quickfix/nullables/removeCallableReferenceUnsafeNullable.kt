// "Remove '?'" "true"
// K2_ERROR: UNSAFE_CALLABLE_REFERENCE
class Foo {
    fun f() = 1
}

fun test() {
    Foo<caret>?::f
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveCallableReferenceStaticLhsFixFactories$RemoveCallableReferenceStaticLhsFix
