// K2-ERROR:
// K2-AFTER-ERROR: Conflicting overloads:<br>fun readResolve(): Any
// K2-AFTER-ERROR: Conflicting overloads:<br>fun readResolve(): Foo
// ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
// ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, private final fun readResolve(): Foo defined in Foo
object Foo<caret> : java.io.Serializable {
    private fun readResolve(): Foo = Foo
}
