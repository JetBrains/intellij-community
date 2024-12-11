// NO_OPTION: CONSTRUCTOR_PARAMETER
// CHOSEN_OPTION: PROPERTY
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}