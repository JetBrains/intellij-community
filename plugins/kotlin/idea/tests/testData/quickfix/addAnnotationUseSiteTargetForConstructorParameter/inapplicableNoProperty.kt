// "Add use-site target 'param'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+AnnotationDefaultTargetMigrationWarning


@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class MyClass(<caret>@Anno val foo: String)
