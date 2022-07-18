class NavigateFromPrivateAbstractClass {
    private abstract class <lineMarker descr="Is subclassed by Impl in NavigateFromPrivateAbstractClass Impl2 in NavigateFromPrivateAbstractClass  Click or press ... to navigate">Base</lineMarker> {
        abstract fun <lineMarker>foo</lineMarker>()
    }

    private class Impl: Base() {
        override fun <lineMarker descr="Implements function in 'Base'">foo</lineMarker>() {}
    }

    private class Impl2: Base() {
        override fun <lineMarker descr="Implements function in 'Base'">foo</lineMarker>() {}
    }

}

fun foo() {
    open class <lineMarker descr="Is subclassed by B in foo() in NavigateFromPrivateAbstractClass.kt C in foo() in NavigateFromPrivateAbstractClass.kt  Click or press ... to navigate">A</lineMarker> {}
    class B : A() {}
    class C : A() {}
}
/*
LINEMARKER: descr='Is subclassed by Impl in NavigateFromPrivateAbstractClass Impl2 in NavigateFromPrivateAbstractClass  Click or press ... to navigate'
TARGETS:
 NavigateFromPrivateAbstractClass.kt
    private class <1>Impl: Base() {
        override fun foo() {}
    }

    private class <2>Impl2: Base() {
*/

/*
LINEMARKER: descr='Is implemented in NavigateFromPrivateAbstractClass.Impl NavigateFromPrivateAbstractClass.Impl2'
TARGETS:
NavigateFromPrivateAbstractClass.kt
        override fun <1>foo() {}
    }

    private class Impl2: Base() {
        override fun <2>foo() {}
*/

/*
LINEMARKER: descr='Implements function in 'Base''
TARGETS:
NavigateFromPrivateAbstractClass.kt
        abstract fun <1>foo()
*/

/*
LINEMARKER: descr='Is subclassed by B in foo() in NavigateFromPrivateAbstractClass.kt C in foo() in NavigateFromPrivateAbstractClass.kt  Click or press ... to navigate'
TARGETS:
NavigateFromPrivateAbstractClass.kt
    class <1>B : A() {}
    class <2>C : A() {}
 */