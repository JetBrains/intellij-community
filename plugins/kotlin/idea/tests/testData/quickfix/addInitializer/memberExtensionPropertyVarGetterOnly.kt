// "Add initializer" "false"
// ACTION: Add setter
// ACTION: Change to 'val'
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Remove explicit type specification
// ERROR: Property must be initialized
// K2_AFTER_ERROR: Extension property must have accessors or be abstract.
class A {
    <caret>var Int.n: Int
        get() = 1
}