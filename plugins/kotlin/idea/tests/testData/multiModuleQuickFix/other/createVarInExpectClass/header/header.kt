// "Create member property 'Foo.foo'" "true"
// IGNORE_K2

expect class Foo

fun test(f: Foo) {
    f.<caret>foo = 1
}