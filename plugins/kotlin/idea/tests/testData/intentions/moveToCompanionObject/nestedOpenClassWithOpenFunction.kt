class Foo {
    open class B<caret>ar {
        open fun nestedFun() {}
        final override fun toString(): String = "Bar"
    }
}