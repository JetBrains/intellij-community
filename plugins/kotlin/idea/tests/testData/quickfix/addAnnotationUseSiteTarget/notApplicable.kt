// "Add use-site target" "false"
// ERROR: This annotation is not applicable to target 'class'

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno

@Anno<caret>
class Foo