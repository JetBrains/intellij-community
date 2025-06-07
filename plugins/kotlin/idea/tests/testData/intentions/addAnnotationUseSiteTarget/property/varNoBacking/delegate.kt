// NO_OPTION: PROPERTY_DELEGATE_FIELD|Add use-site target 'delegate'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'
// AFTER-WARNING: Parameter 'p' is never used

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}