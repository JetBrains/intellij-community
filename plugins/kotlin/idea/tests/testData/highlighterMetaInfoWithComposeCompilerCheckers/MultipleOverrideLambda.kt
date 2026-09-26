// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

class Impl : () -> Unit, @Composable (Int) -> Unit {
    @Composable override fun invoke() {}
    @Composable override fun invoke(p0: Int) {}
}
