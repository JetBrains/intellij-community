// NO_OPTION: PROPERTY_SETTER|Add use-site target 'set'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Property {
    @A<caret>
    val foo: String
        get() = ""
}