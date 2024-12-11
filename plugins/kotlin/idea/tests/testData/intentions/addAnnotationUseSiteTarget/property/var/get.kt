// CHOSEN_OPTION: PROPERTY_GETTER

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}