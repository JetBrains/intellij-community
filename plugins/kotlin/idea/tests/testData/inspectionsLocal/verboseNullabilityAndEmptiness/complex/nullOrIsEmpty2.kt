// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: Smart cast to 'List<Int>' is impossible, because 'list' is a mutable property that could be mutated concurrently.
// K2_ERROR: Smart cast to 'List<Int>' is impossible, because 'list' is a mutable property that could be mutated concurrently.

// ERROR: Smart cast to 'List<Int>' is impossible, because 'foo.bar.list' is a mutable property that could have been changed by this time
// ERROR: Smart cast to 'List<Int>' is impossible, because 'foo.bar.list' is a mutable property that could have been changed by this time

class Foo(val bar: Bar)
class Bar(var list: List<Int>?)

fun test(foo: Foo) {
    if (<caret>foo.bar.list == null || foo.bar.list.isEmpty()) println(0) else println(foo.bar.list.size)
}
