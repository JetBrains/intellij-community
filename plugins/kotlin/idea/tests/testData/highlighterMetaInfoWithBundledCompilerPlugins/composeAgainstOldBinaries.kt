// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// ALLOW_ERRORS
// HIGHLIGHTER_ATTRIBUTES_KEY
// ISSUE: KT-74781
package test

import androidx.compose.runtime.Composable

class Derived : Base() {
    @Composable
    override fun foo(f: @Composable () -> Unit) {}
}
