// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// ALLOW_ERRORS
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

import androidx.compose.runtime.Composable

interface ColumnScope {
}

@Composable
inline fun Column(content: @Composable ColumnScope.() -> Unit) {
}

@Composable
fun Home() {
    Column {
        Greeting("Compose")
    }
}

@Composable
fun Greeting(name: String) = "Hello, $name"

// Calling Composable function from non-Composable function is not allowed.
// ERROR "COMPOSABLE_EXPECTED" is expected.
fun NonComposableFunctionCallingComposable() {
    Home()
}