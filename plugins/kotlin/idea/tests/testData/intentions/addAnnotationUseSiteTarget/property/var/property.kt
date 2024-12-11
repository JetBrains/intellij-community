// CHOSEN_OPTION: PROPERTY

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}