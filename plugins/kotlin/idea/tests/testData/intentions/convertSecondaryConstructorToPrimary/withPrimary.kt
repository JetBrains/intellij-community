// "Convert to primary constructor" "false"
// IS_APPLICABLE: false
// ERROR: Primary constructor call expected
// K2_ERROR: PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED
// K2_AFTER_ERROR: PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED

class WithPrimary() {
    constructor<caret>(x: Int) //comment 1
}