// CHOSEN_OPTION: PROPERTY_GETTER|Add use-site target 'get'

annotation class A

annotation class B

class Test {
    @get:B
    @A<caret>
    val foo: String = ""
}