// "Add new line after annotations" "true"

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun foo(y: Int) {
    var x = 1
    @Ann x<caret> += 2
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNewLineAfterAnnotationsFix