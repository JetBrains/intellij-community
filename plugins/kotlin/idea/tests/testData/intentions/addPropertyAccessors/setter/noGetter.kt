// IS_APPLICABLE: false
// as it is already reported as compiler error
// ERROR: Property must be initialized
// K2_ERROR: MUST_BE_INITIALIZED
class Test {
    var x<caret>:String
        set(value) {}
}