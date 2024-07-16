// "Add getter" "true"
// WITH_STDLIB
class Test {
    var x: Int<caret>
        set(value) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddPropertyGetterIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddAccessorsFactories$AddAccessorsQuickFix