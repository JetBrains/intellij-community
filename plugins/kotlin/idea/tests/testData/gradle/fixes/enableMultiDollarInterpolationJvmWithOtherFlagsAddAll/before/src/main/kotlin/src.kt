// "Configure arguments for the feature: multi dollar interpolation" "true"
// K2_ERROR: The feature "multi dollar interpolation" is only available since language version 2.2
fun test() {
    <caret>$$"$Enable me$"
}
