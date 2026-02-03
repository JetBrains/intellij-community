// NO_OPTION: CONSTRUCTOR_PARAMETER|Add use-site target 'param'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}