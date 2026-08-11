// PROBLEM: none
open class Super {
    public fun readResolve(): Any = Foo
}

object Foo<caret> : Super(), java.io.Serializable
