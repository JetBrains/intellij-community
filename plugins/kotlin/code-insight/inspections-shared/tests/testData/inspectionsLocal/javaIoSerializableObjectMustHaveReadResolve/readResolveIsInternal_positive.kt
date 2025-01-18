// K2-ERROR:
// K2-AFTER-ERROR: Conflicting overloads:<br>fun readResolve(): Any
// K2-AFTER-ERROR: Conflicting overloads:<br>fun readResolve(): Any
// ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, internal final fun readResolve(): Any defined in Foo
// ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, internal final fun readResolve(): Any defined in Foo
object Foo<caret> : java.io.Serializable {
    internal fun readResolve(): Any = Foo
}
