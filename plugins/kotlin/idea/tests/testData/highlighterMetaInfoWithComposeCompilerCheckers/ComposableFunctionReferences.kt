// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

@Composable fun A() {}
val aCallable: () -> Unit = ::A
val bCallable: @Composable () -> Unit = ::A
val cCallable = ::A
fun doSomething(fn: () -> Unit) { print(fn) }
@Composable fun B(content: @Composable () -> Unit) {
    content()
    doSomething(::A)
    B(::A)
}
