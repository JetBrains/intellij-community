// "Remove parameter 'a'" "true"

class A(//TODO Comment 1
    //TODO Comment 2
    <caret>a: Int
    //TODO Comment 3
) //TODO Comment 4

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix