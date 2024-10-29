// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.Composable

@Composable fun main(args: Array<String>) {
    print(args)
}
