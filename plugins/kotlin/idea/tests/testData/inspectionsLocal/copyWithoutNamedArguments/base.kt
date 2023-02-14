// PROBLEM: Parameter names should be specified explicitly for the 'copy()' method call

data class Foo(val a: String)

fun bar(f: Foo) {
    f.co<caret>py("")
}