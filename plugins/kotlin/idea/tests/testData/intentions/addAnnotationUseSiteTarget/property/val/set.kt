// NO_OPTION: PROPERTY_SETTER
// CHOSEN_OPTION: PROPERTY

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}