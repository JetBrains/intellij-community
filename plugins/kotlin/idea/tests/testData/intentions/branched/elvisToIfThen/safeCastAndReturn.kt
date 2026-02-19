// AFTER-WARNING: Variable 'y' is never used
class My(val x: Int)
fun foo(arg: Any) {
    val y = (arg as? My)?.x <caret>?: return
}