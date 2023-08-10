// AFTER-WARNING: Variable 'y' is never used
class My(var z: Any, val x: Int) {
    fun foo() {
        val y = (z as? My)?.x <caret>?: 42
    }
}