// "Add initializer" "false"
// ACTION: Add getter
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ERROR: Property must be initialized
// K2_AFTER_ERROR: Extension property must have accessors or be abstract.
class A {
    <caret>var Int.n: Int
        set(value: Int) {}
}