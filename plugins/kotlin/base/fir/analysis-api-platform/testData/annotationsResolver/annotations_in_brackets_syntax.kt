package foo.bar.baz

annotation class Ann1

annotation class Ann2

annotation class Ann3

@[Ann1]
@[Ann2 Ann2]
fun test<caret>() {}

// ANNOTATION: foo/bar/baz/Ann1, foo/bar/baz/Ann2