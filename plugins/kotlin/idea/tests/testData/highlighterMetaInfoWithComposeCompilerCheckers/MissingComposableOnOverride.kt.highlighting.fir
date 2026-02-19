// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

interface Foo {
    @Composable
    fun composableFunction(param: Boolean): Boolean
    fun nonComposableFunction(param: Boolean): Boolean
    val nonComposableProperty: Boolean
}

object FakeFoo : Foo {
    override <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: fun composableFunction(param: Boolean): Boolean fun composableFunction(param: Boolean): Boolean'")!>fun composableFunction(param: Boolean)<!> = true
    @Composable override <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: fun nonComposableFunction(param: Boolean): Boolean fun nonComposableFunction(param: Boolean): Boolean'")!>fun nonComposableFunction(param: Boolean)<!> = true
    override val nonComposableProperty: Boolean @Composable <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: get(): Boolean get(): Boolean'")!>get()<!> = true
}

interface Bar {
    @Composable
    fun composableFunction(param: Boolean): Boolean
    @get:Composable val composableProperty: Boolean
    fun nonComposableFunction(param: Boolean): Boolean
    val nonComposableProperty: Boolean
}

object FakeBar : Bar {
    override <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: fun composableFunction(param: Boolean): Boolean fun composableFunction(param: Boolean): Boolean'")!>fun composableFunction(param: Boolean)<!> = true
    override <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: get(): Boolean get(): Boolean'")!>val composableProperty: Boolean<!> = true
    @Composable override <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: fun nonComposableFunction(param: Boolean): Boolean fun nonComposableFunction(param: Boolean): Boolean'")!>fun nonComposableFunction(param: Boolean)<!> = true
    override val nonComposableProperty: Boolean @Composable <!HIGHLIGHTING("severity='ERROR'; descr='[CONFLICTING_OVERLOADS] Conflicting overloads: get(): Boolean get(): Boolean'")!>get()<!> = true
}
