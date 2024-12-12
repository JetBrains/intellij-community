// CHOSEN_OPTION: PROPERTY_GETTER
// AFTER-WARNING: Parameter 'p' is never used
// IGNORE_K2
// Issue: KTIJ-32504

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}