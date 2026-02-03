// NO_OPTION: PROPERTY_GETTER|Add use-site target 'get'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY)
@Repeatable
annotation class A

class Test {
    @get:A
    @A<caret>
    val foo: String = ""
}