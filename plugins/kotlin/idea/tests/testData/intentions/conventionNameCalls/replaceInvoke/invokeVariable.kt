// IS_APPLICABLE: false
class Foo {
    operator fun invoke() {}
}

class Boo(val invoke: Foo)

fun test(bar: Boo) {
    bar.invoke<caret>()
}