// "Move to constructor parameters" "false"
// ACTION: Add getter
// ACTION: Add initializer
// ACTION: Initialize with constructor parameter
// ACTION: Make 'n' 'abstract'
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ERROR: Property must be initialized or be abstract
// K2_AFTER_ERROR: Property must be initialized or be abstract.
open class A

class B {
    <caret>val n: Int

    constructor()
}