// "Add setter" "true"
class Test {
    var x: Int<caret>
        get() {
            return 1
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddPropertySetterIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.AddAccessorsFactories$AddAccessorsQuickFix