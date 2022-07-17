class NavigateFromPrivateAbstractClass {
    private abstract class <lineMarker>Base</lineMarker> {
        abstract fun <lineMarker>foo</lineMarker>()
    }

    private class Impl: Base() {
        override fun <lineMarker descr="Implements function in 'Base'">foo</lineMarker>() {}
    }

    private class Impl2: Base() {
        override fun <lineMarker descr="Implements function in 'Base'">foo</lineMarker>() {}
    }

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