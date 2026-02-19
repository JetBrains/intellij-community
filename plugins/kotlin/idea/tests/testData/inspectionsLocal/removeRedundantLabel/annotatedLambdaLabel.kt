// PROBLEM: none

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun testAnnotatedLambdaLabel() = lambda@<caret> @Ann {}
