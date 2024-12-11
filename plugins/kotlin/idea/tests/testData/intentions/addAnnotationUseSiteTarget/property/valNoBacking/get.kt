// CHOSEN_OPTION: PROPERTY_GETTER

annotation class A

class Property {
    @A<caret>
    val foo: String
        get() = ""
}