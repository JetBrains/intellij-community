// "Add use-site target" "false"
// ERROR: This annotation is not applicable to target 'class'
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET
// K2_ERROR: WRONG_ANNOTATION_TARGET

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno

@Anno<caret>
class Foo