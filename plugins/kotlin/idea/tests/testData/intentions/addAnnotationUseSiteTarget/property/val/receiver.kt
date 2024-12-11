// NO_OPTION: RECEIVER
// CHOSEN_OPTION: PROPERTY

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}