// "Configure arguments for the feature: multi dollar interpolation" "true"
// K2_ERROR: UNSUPPORTED_FEATURE
package p

fun test() {
    fun test() {
        $$"<caret>$Enable me$"
    }
}
