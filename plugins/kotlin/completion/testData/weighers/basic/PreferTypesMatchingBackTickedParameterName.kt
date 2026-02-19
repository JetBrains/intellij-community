// IGNORE_K1
package test

open class Apple
class RedApple: Apple()
class GreenApple: Apple()

fun testBackTicks(`red apple`: <caret>) {}

// ORDER: RedApple
// ORDER: Apple
// ORDER: GreenApple