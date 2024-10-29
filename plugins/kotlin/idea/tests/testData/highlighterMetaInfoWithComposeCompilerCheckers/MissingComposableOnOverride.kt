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
    override fun composableFunction(param: Boolean) = true
    @Composable override fun nonComposableFunction(param: Boolean) = true
    override val nonComposableProperty: Boolean @Composable get() = true
}

interface Bar {
    @Composable
    fun composableFunction(param: Boolean): Boolean
    @get:Composable val composableProperty: Boolean
    fun nonComposableFunction(param: Boolean): Boolean
    val nonComposableProperty: Boolean
}

object FakeBar : Bar {
    override fun composableFunction(param: Boolean) = true
    override val composableProperty: Boolean = true
    @Composable override fun nonComposableFunction(param: Boolean) = true
    override val nonComposableProperty: Boolean @Composable get() = true
}
