// K2-AFTER-ERROR: Unresolved reference 'Foo'.
import foo.Bar

class Test {
    val <caret>foo = Bar().foo()
}