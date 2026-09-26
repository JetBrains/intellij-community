// NO_OPTION: CONSTRUCTOR_PARAMETER|Add use-site target 'param'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'
// AFTER-WARNING: Parameter 'p' is never used

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}