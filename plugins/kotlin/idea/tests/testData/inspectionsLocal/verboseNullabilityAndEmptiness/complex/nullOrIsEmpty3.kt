// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'List<Int>?'.
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'List<Int>?'.

// ERROR: Smart cast to 'List<Int>' is impossible, because 'foo.bar().list' is a complex expression
// ERROR: Smart cast to 'List<Int>' is impossible, because 'foo.bar().list' is a complex expression

interface Foo {
    fun bar(): Bar
}

class Bar(var list: List<Int>?)

fun test(foo: Foo) {
    if (<caret>foo.bar().list == null || foo.bar().list.isEmpty()) println(0) else println(foo.bar().list.size)
}
