// NO_OPTION: Add use-site target 'all'
// CHOSEN_OPTION: Add use-site target 'property'
// COMPILER_ARGUMENTS: -XXLanguage:-AnnotationAllUseSiteTarget

annotation class A

class Property {
    @A<caret>
    val foo: String = ""
}