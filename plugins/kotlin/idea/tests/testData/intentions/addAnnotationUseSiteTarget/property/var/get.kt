// CHOSEN_OPTION: PROPERTY_GETTER|Add use-site target 'get'

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}