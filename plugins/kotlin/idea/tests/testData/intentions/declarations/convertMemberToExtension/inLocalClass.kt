// IS_APPLICABLE: false
class Owner {
    fun m() {
        class Local {
            fun <caret>f() {}
        }
    }
}
// IGNORE_K1