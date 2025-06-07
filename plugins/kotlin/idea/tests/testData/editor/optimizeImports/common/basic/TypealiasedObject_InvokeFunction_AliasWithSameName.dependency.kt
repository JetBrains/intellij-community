package dependency

class Holder {
    object WithInvoke {
        operator fun invoke() {}
    }
}

typealias WithInvoke = Holder.WithInvoke
