// K2_AFTER_ERROR: Unresolved reference 'Foo'.
import foo.Bar

class Test {
    val <caret>foo = Bar().foo()
}