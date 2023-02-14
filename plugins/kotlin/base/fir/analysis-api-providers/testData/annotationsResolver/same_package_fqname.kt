package foo.bar.baz

annotation class Ann1

annotation class Ann2

annotation class Ann3

@foo.bar.baz.Ann1
@foo.bar.baz.Ann2
fun test<caret>() {}

// ANNOTATION: foo/bar/baz/Ann1, foo/bar/baz/Ann2