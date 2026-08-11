// "Initialize with constructor parameter" "false"
// ACTION: Add getter
// ACTION: Add initializer
// ACTION: Make internal
// ACTION: Make private
// ERROR: Property must be initialized or be abstract
// K2_AFTER_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
// K2_ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
object A {
    <caret>val n: Int
}