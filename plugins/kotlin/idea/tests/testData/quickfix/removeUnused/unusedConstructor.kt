// K1_ACTION: "Safe delete constructor" "true"
// K2_ACTION: "Safe delete secondary constructor 'Owner'" "true"
class Owner(val x: Int) {
    <caret>constructor(): this(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix