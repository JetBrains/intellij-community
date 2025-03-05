// "Add compiler argument: -Xannotation-default-target=param-property" "true"

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
internal annotation class Anno

class MyClass(@Anno<caret> val foo: String)