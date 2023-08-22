object Foo<caret> : java.io.Serializable {
    private fun readResolve(param: Int): Any = Foo
}
