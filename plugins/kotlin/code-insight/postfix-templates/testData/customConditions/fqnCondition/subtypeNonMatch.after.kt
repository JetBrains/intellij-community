// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:Fruit
// USE_TOPMOST: false
open class Fruit
class Vegetable

fun test(veggie: Vegetable) {
    veggie.fqnCondition
}
