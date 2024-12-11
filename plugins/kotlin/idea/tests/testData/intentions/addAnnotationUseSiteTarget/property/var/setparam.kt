// CHOSEN_OPTION: SETTER_PARAMETER

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}