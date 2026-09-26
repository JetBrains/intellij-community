// PROBLEM: none
open class Super {
    protected fun readResolve(): Any = Foo
}

object Foo<caret> : Super(), java.io.Serializable
