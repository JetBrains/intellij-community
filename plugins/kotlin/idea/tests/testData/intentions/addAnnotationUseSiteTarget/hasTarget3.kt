// CHOSEN_OPTION: PROPERTY_GETTER

annotation class A

annotation class B

class Test {
    @get:B
    @A<caret>
    val foo: String = ""
}