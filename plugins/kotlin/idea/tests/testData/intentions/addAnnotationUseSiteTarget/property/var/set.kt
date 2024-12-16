// CHOSEN_OPTION: PROPERTY_SETTER|Add use-site target 'set'

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}