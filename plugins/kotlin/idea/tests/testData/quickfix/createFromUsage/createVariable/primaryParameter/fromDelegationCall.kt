// "Create property 'foo' as constructor parameter" "false"
// ERROR: There's a cycle in the delegation calls chain
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: There's a cycle in the delegation calls chain.
// K2_AFTER_ERROR: Unresolved reference 'foo'.
// IGNORE_K1

class CtorAccess() {
    constructor(ps: String) : this(fo<caret>o)
}