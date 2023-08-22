// "Safe delete constructor" "true"
class Owner(val x: Int) {
    <caret>constructor(): this(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix