// CHOOSE_USE_SITE_TARGET: get
// AFTER-WARNING: Parameter 'p' is never used

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}