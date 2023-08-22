// WITH_STDLIB

class Foo(val bar: Bar)
class Bar(val list: List<Int>?)

fun test(foo: Foo) {
    if (<caret>foo.bar.list == null || foo.bar.list.isEmpty()) println(0) else println(foo.bar.list.size)
}
