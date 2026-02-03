// IS_APPLICABLE: false
// as it is already reported as compiler error
// ERROR: Property must be initialized
// K2_ERROR: Property must be initialized.
class Test {
    var x<caret>:String
        set(value) {}
}