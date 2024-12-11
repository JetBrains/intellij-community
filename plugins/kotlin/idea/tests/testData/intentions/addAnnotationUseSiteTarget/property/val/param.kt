// NO_OPTION: CONSTRUCTOR_PARAMETER
// CHOSEN_OPTION: PROPERTY

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}