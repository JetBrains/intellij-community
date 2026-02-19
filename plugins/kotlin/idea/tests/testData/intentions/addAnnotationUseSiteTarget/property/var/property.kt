// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}