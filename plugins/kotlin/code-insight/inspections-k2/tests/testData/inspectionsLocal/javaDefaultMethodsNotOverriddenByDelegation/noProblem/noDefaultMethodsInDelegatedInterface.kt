// PROBLEM: none
open class IImpl : InterfaceWithoutDefaultMethod

fun main() {
    val iImpl = IImpl()
    object : InterfaceWithDefaultMethod, InterfaceWithoutDefaultMethod by iImpl<caret> {}
}
