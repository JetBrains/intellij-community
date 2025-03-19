// LANGUAGE_VERSION: 2.0
// PROBLEM: none

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class Ignored {
    <caret>@Anno val bar: String = ""
}
