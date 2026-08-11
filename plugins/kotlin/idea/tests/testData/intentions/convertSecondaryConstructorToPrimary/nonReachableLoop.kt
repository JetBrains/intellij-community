// "Convert to primary constructor" "false"
// IS_APPLICABLE: false
// ERROR: There's a cycle in the delegation calls chain
// ERROR: There's a cycle in the delegation calls chain
// K2_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL

class NonReachableLoop {
    constructor<caret>(x: String)

    constructor(x: Int, y: Int): this(x + y)

    constructor(x: Int): this(x, x)
}