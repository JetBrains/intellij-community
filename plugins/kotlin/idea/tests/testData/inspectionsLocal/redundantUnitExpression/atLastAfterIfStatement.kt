// PROBLEM: none
enum class RPC { Rock, Paper }

fun rpc(rpc: RPC) {
    when (rpc) {
        RPC.Rock -> {
        }
        RPC.Paper -> {
            if (true) {
                println("im true")
            }
            <caret>Unit
        }
    }.exhaustive
}

inline val Unit.exhaustive: Unit
    get() = Unit

fun println(s: String) {}