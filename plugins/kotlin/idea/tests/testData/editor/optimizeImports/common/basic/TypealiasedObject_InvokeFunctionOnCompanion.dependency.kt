package dependency

interface WithCompanionWithInvoke {
    companion object {
        operator fun invoke() {}
    }
}

typealias WithCompanionWithInvokeAlias = WithCompanionWithInvoke
