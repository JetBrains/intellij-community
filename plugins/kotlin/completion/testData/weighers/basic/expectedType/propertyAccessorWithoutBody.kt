class Foo

val somePrefixA: Int = 5
val somePrefixB: Foo = Foo()
val somePrefixC: Int = 5

val testing: Foo
    get() = somePrefix<caret>

// ORDER: somePrefixB, somePrefixA, somePrefixC