// "Create member property 'Foo.foo'" "true"
// IGNORE_K2

expect class Foo

fun test(f: Foo) {
    takeInt(f.<caret>foo)
}

fun takeInt(n: Int) {

}