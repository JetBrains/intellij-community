import Foo.Companion.foo

class Foo {
    companion object { fun foo() = "" }
}
fun test() = Foo.Companion.<caret>foo()