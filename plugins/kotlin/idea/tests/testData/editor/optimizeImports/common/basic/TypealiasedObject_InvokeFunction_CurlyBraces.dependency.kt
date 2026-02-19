package dependency

object WithLambdaInvoke {
    operator fun invoke(action: () -> Unit) {}
}

typealias WithLambdaInvokeAlias = WithLambdaInvoke
