// CHOSEN_OPTION: PROPERTY_DELEGATE_FIELD
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}