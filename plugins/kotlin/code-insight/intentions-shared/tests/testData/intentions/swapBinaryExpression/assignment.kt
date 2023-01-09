// IS_APPLICABLE: false
// AFTER-WARNING: Variable 'mutablevar' is assigned but never accessed
fun main() {
    var mutablevar = 20
    mutablevar = <caret>10
}