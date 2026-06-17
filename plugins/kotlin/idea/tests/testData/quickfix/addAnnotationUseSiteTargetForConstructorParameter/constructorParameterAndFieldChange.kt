// "Change use-site target to 'field'" "true"
// LANGUAGE_VERSION: 2.3
// ACTION "Add use-site target 'param'"
// COMPILER_ARGUMENTS: -XXLanguage:+AnnotationDefaultTargetMigrationWarning


@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class MyClass(<caret>@Anno val foo: String)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$ChangeConstructorParameterUseSiteTargetFix