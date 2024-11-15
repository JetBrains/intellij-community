// PROBLEM: none
open class IImpl : InterfaceWithDefaultMethod, InterfaceWithoutDefaultMethod

fun main() {
    val iImpl = IImpl()
    object : InterfaceWithoutDefaultMethod by iImpl<caret> {}
}
