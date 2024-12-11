// CHOSEN_OPTION: PROPERTY_GETTER
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}