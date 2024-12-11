// NO_OPTION: PROPERTY_DELEGATE_FIELD
// CHOSEN_OPTION: PROPERTY

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}