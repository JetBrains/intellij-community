// "Opt in for 'A' on 'root'" "true"
// PRIORITY: HIGH
// K2_ERROR: OPT_IN_USAGE_ERROR
@RequiresOptIn
annotation class A

@A
fun f1() {}

@OptIn
fun root() {
    <caret>f1()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$ModifyOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$ModifyOptInAnnotationFix