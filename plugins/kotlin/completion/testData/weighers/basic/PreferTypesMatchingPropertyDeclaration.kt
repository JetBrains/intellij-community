package test

open class Apple
class GreenApple: Apple()
class RedApple: Apple()

val apple : <caret>

// ORDER: Apple
// ORDER: GreenApple
// ORDER: RedApple
