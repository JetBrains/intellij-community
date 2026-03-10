// "Add setter" "true"
// K2_ERROR: Property must be initialized.
class Test {
    var x: Int<caret>
        get() {
            return 1
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddPropertySetterIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddAccessorsFactories$AddAccessorsQuickFix