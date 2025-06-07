// CHOSEN_OPTION: SETTER_PARAMETER|Add use-site target 'setparam'

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}