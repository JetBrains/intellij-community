// IGNORE_K1
package test

open class Apple
class `Red Apple`: Apple()
class GreenApple: Apple()

fun testBackTicks(redApple: <caret>) {}

// ORDER: Red Apple
// ORDER: Apple
// ORDER: GreenApple