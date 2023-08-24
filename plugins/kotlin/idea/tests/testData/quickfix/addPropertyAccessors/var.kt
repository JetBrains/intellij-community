// "Add getter and setter" "true"
// WITH_STDLIB
class Test {
    var x: Int<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddPropertyAccessorsIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.AddAccessorsFactories$AddAccessorsQuickFix