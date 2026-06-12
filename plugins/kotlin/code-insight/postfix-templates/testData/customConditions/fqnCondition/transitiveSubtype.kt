// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:Fruit
// USE_TOPMOST: false
open class Fruit
open class Apple : Fruit()
class Gala : Apple()

fun test(gala: Gala) {
    gala<caret>
}
