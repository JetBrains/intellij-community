open class Super {
    private fun readResolve(): Any = Foo
}

object Foo<caret> : Super(), java.io.Serializable
