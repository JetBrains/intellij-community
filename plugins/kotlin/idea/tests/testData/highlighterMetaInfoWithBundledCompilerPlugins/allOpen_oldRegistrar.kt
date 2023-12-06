// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR/allOpen_fake_plugin_old_registrar.jar -P plugin:org.jetbrains.kotlin.allopen:annotation=test.MyOpener
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

annotation class MyOpener

@MyOpener
class BaseClass

class Child : BaseClass()

/**
 * allOpen_fake_plugin_old_registrar.jar has ComponentRegistrar file instead of CompilerPluginRegistrar inside of it,
 * it imitates an older version of compiler plugin layout.
 *
 * We want to make sure that such jars are also recognized as our own compiler plugins, and are also replaced by the
 * bundled plugins.
 *
 * Otherwise, K2 IDE might break if user chooses an older version of Kotlin for the project
 * (and for the downloaded compiler plugins).
 *
 * See KT-52665 for the details of ComponentRegistrar -> CompilerPluginRegistrar migration.
 */