// "Implement as constructor parameters" "false"
// K2_AFTER_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
interface I {
    val Int.foo: Int
}

<caret>class A : I

