// AFTER_ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
// AFTER_ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
// K2_AFTER_ERROR: CONFLICTING_OVERLOADS
// K2_AFTER_ERROR: CONFLICTING_OVERLOADS
object Foo<caret> : java.io.Serializable {
    private fun readResolve(): Foo = Foo
}
