// "Move to constructor parameters" "false"
// ACTION: Add getter
// ACTION: Add initializer
// ACTION: Make internal
// ACTION: Make private
// ERROR: Property must be initialized or be abstract
object A {
    <caret>val n: Int
}