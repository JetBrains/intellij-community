// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

@Composable suspend fun Foo() {}

fun acceptSuspend(fn: suspend () -> Unit) { print(fn) }
fun acceptComposableSuspend(fn: @Composable suspend () -> Unit) { print(fn.hashCode()) }

val foo: suspend () -> Unit = @Composable {}
val bar: suspend () -> Unit = {}
fun Test() {
    val composableLambda = @Composable {}
    acceptSuspend @Composable {}
    acceptComposableSuspend @Composable {}
    acceptComposableSuspend(composableLambda)
    acceptSuspend(@Composable suspend fun() { })
}
