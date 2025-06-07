// NO_OPTION: FIELD|Add use-site target 'field'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Property {
    @A<caret>
    val foo: String
        get() = ""
}