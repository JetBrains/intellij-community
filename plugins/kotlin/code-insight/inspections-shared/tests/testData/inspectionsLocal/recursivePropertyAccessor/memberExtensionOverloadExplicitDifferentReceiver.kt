// PROBLEM: none

class Bar

class Foo {
    val p: Any
        get() = 42

    val Bar.p: Any
        get() = this@Foo.p<caret>
}
