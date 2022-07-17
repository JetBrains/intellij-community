// "Add use-site target 'get'" "true"
@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno2

@<caret>Anno2
var b = 42
