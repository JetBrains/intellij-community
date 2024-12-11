// CHOSEN_OPTION: FIELD

annotation class A

class Property {
    @A<caret>
    var foo: String = ""
}