// "Add use-site target" "false"
// ERROR: This annotation is not applicable to target 'class'
// K2_AFTER_ERROR: This annotation is not applicable to target 'class'. Applicable targets: getter, setter

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno

@Anno<caret>
class Foo