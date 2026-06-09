// AFTER_ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
// AFTER_ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
// K2_AFTER_ERROR: Conflicting overloads:<br>fun readResolve(): Any
// K2_AFTER_ERROR: Conflicting overloads:<br>fun readResolve(): Foo
object Foo<caret> : java.io.Serializable {
    private fun readResolve(): Foo = Foo
}
