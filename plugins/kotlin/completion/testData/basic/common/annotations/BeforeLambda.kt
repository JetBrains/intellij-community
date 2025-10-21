@Target(allowedTargets = [AnnotationTarget.EXPRESSION])
@Retention(AnnotationRetention.SOURCE)
annotation class AnnotationForLambda

fun foo(block: () -> Unit) = block()

fun main() {
    foo @AnnotationForLam<caret> {}
}

// EXIST: AnnotationForLambda
// IGNORE_K1
// IGNORE_K2