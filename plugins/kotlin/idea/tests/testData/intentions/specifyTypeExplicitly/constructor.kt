// IS_APPLICABLE: false
// ERROR: Primary constructor call expected
// K2_ERROR: PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED
class A(val x: Int) {
    constructor(x: String)<caret>
}
