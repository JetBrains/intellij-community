// IS_APPLICABLE: false

class Foo {
    companion object {
        fun bar() {}
    }
}

val ref: (Foo.Companion) -> Unit = {<caret> it.bar() }