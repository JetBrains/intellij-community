// "Add getter" "true"
// WITH_STDLIB
class Test {
    val x: Int<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddPropertyGetterIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddAccessorsFactories$AddAccessorsQuickFix