// "Change use-site target to 'all'" "true"
// ACTION "Add use-site target 'param'"
// ACTION "Change use-site target to 'field'"
// COMPILER_ARGUMENTS: -Xannotation-target-all -XXLanguage:+AnnotationDefaultTargetMigrationWarning
// IGNORE_K1

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class MyClass(<caret>@Anno val foo: String)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$ChangeConstructorParameterUseSiteTargetFix