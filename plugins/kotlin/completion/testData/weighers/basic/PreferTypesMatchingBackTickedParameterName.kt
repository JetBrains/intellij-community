
package test

open class Apple
class RedApple: Apple()
class GreenApple: Apple()

fun testBackTicks(`red apple`: <caret>) {}

// ORDER: RedApple
// ORDER: Apple
// ORDER: GreenApple