// IGNORE_K1
package test

open class MyApple
class Apple: MyApple()
class RedApple: MyApple()

@Target(AnnotationTarget.TYPE)
annotation class Ann

fun testAnnotatedType(apple: @Ann <caret>) {}

// ORDER: Apple
// ORDER: MyApple
// ORDER: RedApple
