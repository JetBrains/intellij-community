// CHOSEN_OPTION: PROPERTY_GETTER|Add use-site target 'get'
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}