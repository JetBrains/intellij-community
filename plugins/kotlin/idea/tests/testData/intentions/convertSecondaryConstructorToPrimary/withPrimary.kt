// "Convert to primary constructor" "false"
// IS_APPLICABLE: false
// ERROR: Primary constructor call expected
// K2_AFTER_ERROR: Primary constructor call expected.

class WithPrimary() {
    constructor<caret>(x: Int) //comment 1
}