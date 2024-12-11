// CHOSEN_OPTION: FIELD

annotation class A(val s: String)

class Test {
    @A("...")<caret>
    val foo: String = ""
}