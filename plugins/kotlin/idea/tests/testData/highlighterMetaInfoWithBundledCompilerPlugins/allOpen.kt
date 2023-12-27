// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/allOpen_fake_plugin.jar -P plugin:org.jetbrains.kotlin.allopen:annotation=test.MyOpener
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

annotation class MyOpener

@MyOpener
class BaseClass

class Child : BaseClass()
