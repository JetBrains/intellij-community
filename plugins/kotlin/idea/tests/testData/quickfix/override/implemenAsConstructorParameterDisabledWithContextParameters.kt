// "Implement as constructor parameters" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
interface I {
    context(s: Int)
    val foo: Int
}

<caret>class A : I

