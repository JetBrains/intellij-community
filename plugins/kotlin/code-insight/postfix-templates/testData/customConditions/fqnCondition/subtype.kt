// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:Fruit
// USE_TOPMOST: false
open class Fruit
class Apple : Fruit()

fun test(apple: Apple) {
    apple<caret>
}
