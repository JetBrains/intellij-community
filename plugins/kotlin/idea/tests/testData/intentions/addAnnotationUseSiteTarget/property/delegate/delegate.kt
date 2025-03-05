// CHOSEN_OPTION: PROPERTY_DELEGATE_FIELD|Add use-site target 'delegate'
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}