// AFTER-WARNING: Parameter 'x' is never used
class Foo<in T> {
    fun <caret>bar(x: T) {}
}