// IGNORE_K1
// WITH_STDLIB
package test
import kotlin.random.Random as Apple

class GreenApple
class RedApple

val apple: <caret>

// ORDER: Apple
// ORDER: GreenApple
// ORDER: RedApple
