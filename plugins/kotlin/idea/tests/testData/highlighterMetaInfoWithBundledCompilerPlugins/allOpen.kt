// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR/allOpen_fake_plugin.jar -P plugin:org.jetbrains.kotlin.allopen:annotation=test.MyOpener
// FILE: main.kt
package test

annotation class MyOpener

@MyOpener
class BaseClass

class Child : BaseClass()
