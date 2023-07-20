// ERROR: 'readResolve' hides member of supertype 'Super' and needs 'override' modifier
// ERROR: Cannot weaken access privilege 'internal' for 'readResolve' in 'Super'
open class Super {
    internal fun readResolve(): Any = Foo
}

object Foo<caret> : Super(), java.io.Serializable
