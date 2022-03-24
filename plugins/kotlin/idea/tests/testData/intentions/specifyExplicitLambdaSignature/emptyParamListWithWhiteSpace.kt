// AFTER-WARNING: Variable 'oom' is never used
fun main() {
    val oom: (Int)->Int = {<caret>
        it * 2
    }
}