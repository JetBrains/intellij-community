// CHOSEN_OPTION: FIELD

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}