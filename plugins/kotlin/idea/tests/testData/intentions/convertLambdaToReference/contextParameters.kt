// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
class Foo {
    context(a: String) fun foo() {}
    context(a: String) fun bar() { run { f<caret>oo() } }
}