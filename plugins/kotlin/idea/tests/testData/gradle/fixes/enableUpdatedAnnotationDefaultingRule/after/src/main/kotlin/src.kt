// "Enable the future default in compiler arguments ('param' + 'property')" "true"

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
internal annotation class Anno

class MyClass(@Anno val foo: String)