// CHOSEN_OPTION: FIELD|Add use-site target 'field'

annotation class A(val s: String)

class Test {
    @A("...")<caret>
    val foo: String = ""
}