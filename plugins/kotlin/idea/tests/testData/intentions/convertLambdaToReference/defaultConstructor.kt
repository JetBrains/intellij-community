// AFTER-WARNING: Parameter 'f' is never used
class Foo
fun usage(f: () -> Foo) {}
fun test() {
    usage {<caret> Foo() }
}