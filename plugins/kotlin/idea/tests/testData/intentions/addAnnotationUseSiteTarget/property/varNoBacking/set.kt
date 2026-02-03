// CHOSEN_OPTION: PROPERTY_SETTER|Add use-site target 'set'
// AFTER-WARNING: Parameter 'p' is never used

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}