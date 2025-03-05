// NO_OPTION: FILE|Add use-site target 'file'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Property {
    @A<caret>
    val foo: String
        get() = ""
}