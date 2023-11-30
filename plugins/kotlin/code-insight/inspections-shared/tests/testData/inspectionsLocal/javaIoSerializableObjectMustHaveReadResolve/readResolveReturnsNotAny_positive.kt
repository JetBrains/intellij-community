// ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
// ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
object Foo<caret> : java.io.Serializable {
    private fun readResolve(): Foo = Foo
}
