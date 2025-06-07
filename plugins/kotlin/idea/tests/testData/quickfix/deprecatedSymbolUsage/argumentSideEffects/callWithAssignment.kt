// "Replace with 'view.elevation = elevationValue'" "true"
class View {
    var elevation: Int = 0
}

object ViewCompat {
    @Deprecated(
        message = "Please don't use this anymore",
        replaceWith = ReplaceWith("view.elevation = elevationValue")
    )
    fun setElevation(view: View, elevationValue: Int) {
        view.elevation = elevationValue
    }
}

class Main {
    fun main() {
        ViewCompat.setE<caret>levation(View(), 10)
    }
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
