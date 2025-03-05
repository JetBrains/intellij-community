// "Initialize with constructor parameter" "false"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: Property must be initialized.
class A {
    <caret>var : Int
        get() = 1
}