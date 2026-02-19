// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

fun <T> identity(value: T): T = value

// We should infer `ComposableFunction0<Unit>` for `T`
val cl = identity(@Composable {})
val l: () -> Unit = cl
