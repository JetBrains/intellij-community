// COMPILER_ARGUMENTS: -Xplugin=non_existent_location/kotlin-allopen-dev.jar -P plugin:org.jetbrains.kotlin.allopen:annotation=test.MyOpener
// FILE: main.kt
package test

annotation class MyOpener

@MyOpener
class BaseClass

class Child : BaseClass()
