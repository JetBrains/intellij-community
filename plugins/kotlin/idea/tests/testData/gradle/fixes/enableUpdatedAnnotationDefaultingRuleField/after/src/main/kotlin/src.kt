// "Add compiler argument: -Xannotation-default-target=param-property" "true"

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
internal annotation class Anno

class MyClass(@Anno val foo: String)