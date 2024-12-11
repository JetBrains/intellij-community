// CHOSEN_OPTION: PROPERTY
// AFTER-WARNING: Parameter 'p' is never used

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}