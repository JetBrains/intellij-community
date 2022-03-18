// WITH_STDLIB
// PROBLEM: none

// ERROR: Smart cast to 'List<Int>' is impossible, because 'foo.bar().list' is a complex expression
// ERROR: Smart cast to 'List<Int>' is impossible, because 'foo.bar().list' is a complex expression

interface Foo {
    fun bar(): Bar
}

class Bar(var list: List<Int>?)

fun test(foo: Foo) {
    if (<caret>foo.bar().list == null || foo.bar().list.isEmpty()) println(0) else println(foo.bar().list.size)
}
