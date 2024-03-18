// "Create abstract property 'foo'" "false"
// ERROR: Unresolved reference: foo
class A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(<caret>foo)
    }
}