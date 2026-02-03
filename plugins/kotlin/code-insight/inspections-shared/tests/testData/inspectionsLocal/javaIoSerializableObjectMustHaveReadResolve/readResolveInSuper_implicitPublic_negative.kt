// PROBLEM: none
open class Super {
    fun readResolve(): Any = Foo
}

object Foo<caret> : Super(), java.io.Serializable