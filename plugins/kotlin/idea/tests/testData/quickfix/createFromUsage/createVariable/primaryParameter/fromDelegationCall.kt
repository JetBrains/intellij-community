// "Create property 'foo' as constructor parameter" "false"
// ERROR: There's a cycle in the delegation calls chain
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_ERROR: UNRESOLVED_REFERENCE


class CtorAccess() {
    constructor(ps: String) : this(fo<caret>o)
}