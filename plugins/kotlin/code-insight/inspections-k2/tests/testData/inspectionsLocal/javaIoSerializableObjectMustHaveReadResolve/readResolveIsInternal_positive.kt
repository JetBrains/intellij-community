// AFTER_ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, internal final fun readResolve(): Any defined in Foo
// AFTER_ERROR: Conflicting overloads: private final fun readResolve(): Any defined in Foo, internal final fun readResolve(): Any defined in Foo
// K2_AFTER_ERROR: CONFLICTING_OVERLOADS
// K2_AFTER_ERROR: CONFLICTING_OVERLOADS
object Foo<caret> : java.io.Serializable {
    internal fun readResolve(): Any = Foo
}
