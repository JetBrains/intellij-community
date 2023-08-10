// "Add parameter to function 'doSmthWithChild'" "true"
// DISABLE-ERRORS

interface Parent
interface Child : Parent

fun doSmthWithChild(a: Child) {}

fun foobar(parent: Parent) {
    if (parent is Child) {
        doSmthWithChild(parent, <caret>123)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix