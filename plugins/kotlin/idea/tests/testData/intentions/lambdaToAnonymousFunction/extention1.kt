// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'f' is never used
class Foo
fun bar(f: Foo.() -> Unit) {}

fun main(args: Array<String>) {
    bar {<caret>}
}