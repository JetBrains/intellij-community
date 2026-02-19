// CHOSEN_OPTION: FIELD|Add use-site target 'field'

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}