@file:Suppress("UNUSED_PARAMETER")

package sample

actual data class <!LINE_MARKER("descr='Has expects in common module'; targets=[(text=A; container=common; icon=nodes/Module.svg); (text=x; container=common; icon=nodes/Module.svg); (text=y; container=common; icon=nodes/Module.svg)]")!>A<!>(actual val x: Int, actual val y: Double, val t: String)  {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonFun<!>() {}
    fun platformFun() {}

    actual val <!LINE_MARKER("descr='Has expects in common module'")!>z<!>: String by lazy { "" }

    operator fun iterator(): Iterator<Int> = null!!
}

fun testDelegate(): String = getCommonA().z
