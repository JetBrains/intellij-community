// FIR_COMPARISON
// FIR_IDENTICAL
/**
 * @sample someProperty<caret>
 */
class Foo(private val someProperty: Int)

// EXIST: someProperty
// INVOCATION_COUNT: 1