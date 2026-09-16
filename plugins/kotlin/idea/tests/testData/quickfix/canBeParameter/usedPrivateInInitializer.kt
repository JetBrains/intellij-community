// "Remove 'val' from parameter" "true"
class UsedInProperty(private <caret>val x: Int) {
    var y: String

    init {
        y = x.toString()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveValVarFix