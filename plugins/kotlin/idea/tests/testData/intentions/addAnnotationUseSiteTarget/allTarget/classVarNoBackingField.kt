// CHOSEN_OPTION: Add use-site target 'all'
// AFTER-WARNING: Parameter 'p' is never used
// COMPILER_ARGUMENTS: -Xannotation-target-all

annotation class A

class Property {
    @A<caret>
    var foo: String
        get() = ""
        set(p) {}
}