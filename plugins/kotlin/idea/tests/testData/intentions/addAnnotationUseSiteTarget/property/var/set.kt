// CHOSEN_OPTION: PROPERTY_SETTER

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}