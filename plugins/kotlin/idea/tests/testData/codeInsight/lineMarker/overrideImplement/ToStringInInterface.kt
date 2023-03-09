public interface Foo {
    override fun <lineMarker descr="Overrides function in Any (kotlin) Press ... to navigate">toString</lineMarker>() = "str"
}

/*
LINEMARKER: descr='Overrides function in Any (kotlin) Press ... to navigate'
TARGETS:
kotlin.kotlin_builtins
    public open fun <1>toString(): kotlin.String { /* compiled code */ }
*/
