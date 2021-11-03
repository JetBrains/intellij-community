// "Remove @JvmField annotation" "true"
// WITH_RUNTIME
interface I {
    val x: Int
}

class C1 : I { <caret>@JvmField override val x: Int = 1 }