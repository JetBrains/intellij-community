// PROBLEM: none
// LANGUAGE_VERSION: 2.0

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class MyClass(<caret>@Anno val foo: String)
