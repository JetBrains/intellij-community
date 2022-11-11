class NavigateFromPrivateAbstractClass {
    private abstract class <lineMarker descr="Is subclassed by Impl in NavigateFromPrivateAbstractClass Impl2 in NavigateFromPrivateAbstractClass  Click or press ... to navigate">Base</lineMarker> {
        abstract fun <lineMarker descr="Is implemented in  NavigateFromPrivateAbstractClass.Impl  NavigateFromPrivateAbstractClass.Impl2">foo</lineMarker>()
    }

    private class Impl: Base() {
        override fun <lineMarker descr="Implements function in 'Base'">foo</lineMarker>() {}
    }

    private class Impl2: Base() {
        override fun <lineMarker descr="Implements function in 'Base'">foo</lineMarker>() {}
    }

}

class NestedPrivateClass {
    private abstract class <lineMarker descr="Is subclassed by Dsds in Base in NestedPrivateClass  Click or press ... to navigate">Base</lineMarker> {
        private abstract class Dsds : Base()
    }
}

fun localFunctionCase() {
    open class <lineMarker descr="Is subclassed by B in localFunctionCase() in NavigateFromPrivateAbstractClass.kt C in localFunctionCase() in NavigateFromPrivateAbstractClass.kt  Click or press ... to navigate">A</lineMarker> {}
    class B : A() {}
    class C : A() {}
}

fun localFunctionCase2() {
    open class <lineMarker descr="Is subclassed by D in localFunctionCase2() in NavigateFromPrivateAbstractClass.kt  Click or press ... to navigate">A</lineMarker> {
        open fun <lineMarker descr="Is overridden in D in localFunctionCase2() in NavigateFromPrivateAbstractClassKt">foo</lineMarker>() = Unit
    }

    fun doo() {
        class D : A() {
            override fun <lineMarker descr="Overrides function in 'A'">foo</lineMarker>() {}
        }
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
LINEMARKER: descr='Is subclassed by Dsds in NestedPrivateClass  Click or press ... to navigate'
TARGETS:
*/

/*
LINEMARKER: descr='Is implemented in NavigateFromPrivateAbstractClass.Impl NavigateFromPrivateAbstractClass.Impl2'
TARGETS:
NavigateFromPrivateAbstractClass.kt
        override fun <1>localFunctionCase() {}
    }

    private class Impl2: Base() {
        override fun <2>localFunctionCase() {}
*/

/*
LINEMARKER: descr='Implements function in 'Base''
TARGETS:
NavigateFromPrivateAbstractClass.kt
        abstract fun <1>localFunctionCase()
*/

/*
LINEMARKER: descr='Is subclassed by B in localFunctionCase() in NavigateFromPrivateAbstractClass.kt C in localFunctionCase() in NavigateFromPrivateAbstractClass.kt  Click or press ... to navigate'
TARGETS:
NavigateFromPrivateAbstractClass.kt
    class <1>B : A() {}
    class <2>C : A() {}
 */

/*
LINEMARKER: descr='Is subclassed by D in localFunctionCase2() in NavigateFromPrivateAbstractClass.kt  Click or press ... to navigate'
TARGETS:
NavigateFromPrivateAbstractClass.kt
        class <1>D : A() {
 */