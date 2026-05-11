// "Create abstract property 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_ERROR: Unresolved reference 'foo' on receiver of type 'B'.
// K2_AFTER_ERROR: Unresolved reference 'foo' on receiver of type 'B'.
abstract class A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(B().<caret>foo)
    }
}

class B {

}