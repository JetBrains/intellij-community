// NO_OPTION: Add use-site target 'all'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'
// COMPILER_ARGUMENTS: -Xannotation-target-all

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}