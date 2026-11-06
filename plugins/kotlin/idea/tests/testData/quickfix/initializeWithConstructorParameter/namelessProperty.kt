// "Initialize with constructor parameter" "false"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_AFTER_ERROR: SYNTAX
// K2_ERROR: MUST_BE_INITIALIZED
// K2_ERROR: SYNTAX
class A {
    <caret>var : Int
        get() = 1
}