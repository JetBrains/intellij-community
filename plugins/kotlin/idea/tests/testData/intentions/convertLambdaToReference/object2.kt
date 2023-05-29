// IS_APPLICABLE: false

object Foo {
    fun bar() {}
}

val ref: (Foo) -> Unit = {<caret> it.bar() }