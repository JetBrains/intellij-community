// IS_APPLICABLE: false

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.EXPRESSION)
annotation class Anno

val l: <caret>Long = +(@Anno 10)
