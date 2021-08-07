sealed class Base
class Child1 : Base()
class Child2 : Base()

fun test(value: Base) {
    value.when<caret>
}