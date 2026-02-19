// FIR_COMPARISON
// FIR_IDENTICAL
/**
 * @see someProperty<caret>
 */
class Foo(private val someProperty: Int)

// EXIST: someProperty
// INVOCATION_COUNT: 1