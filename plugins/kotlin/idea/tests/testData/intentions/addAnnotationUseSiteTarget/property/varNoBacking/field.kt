// NO_OPTION: FIELD|Add use-site target 'field'
// CHOSEN_OPTION: PROPERTY_GETTER|Add use-site target 'get'
// AFTER-WARNING: Parameter 'p' is never used

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}