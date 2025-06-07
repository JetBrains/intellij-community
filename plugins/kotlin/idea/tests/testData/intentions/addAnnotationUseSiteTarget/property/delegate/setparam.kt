// NO_OPTION: SETTER_PARAMETER|Add use-site target 'setparam'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}