// "Enable the future default in compiler arguments ('param' + 'field')" "true"

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
internal annotation class Anno

class MyClass(@Anno val foo: String)