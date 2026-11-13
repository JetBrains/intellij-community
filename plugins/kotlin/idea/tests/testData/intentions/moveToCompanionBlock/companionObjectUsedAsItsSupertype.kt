// COMPILER_ARGUMENTS: -Xcompanion-blocks

open class HostBase {
    open fun baseFun() = 10
}

class D3Host {
    companion object : HostBase() {
        fun on<caret>ly() = 1
    }
}

val asBase: HostBase = D3Host