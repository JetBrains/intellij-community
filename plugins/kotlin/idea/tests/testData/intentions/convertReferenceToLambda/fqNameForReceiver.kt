// AFTER-WARNING: Variable 'f' is never used
class Foo {
    class Bar {
        fun foo() {}
    }
}

class Bar {
    fun foo() {}
}

fun use() {
    val f: (Foo.Bar) -> Unit = <caret>Foo.Bar::foo
}