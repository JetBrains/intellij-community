// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS
import androidx.compose.runtime.*
@Composable fun C(): Int { return 123 }
val ncProp: Int get() = C()
