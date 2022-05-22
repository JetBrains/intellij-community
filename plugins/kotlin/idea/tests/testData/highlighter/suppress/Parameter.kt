class A(
    @Suppress("<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>") val a: Int
) {
    fun d(@Suppress("<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>") <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning>: Int) = Unit
}

fun d(@Suppress("<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>") <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning>: Int) = Unit

// NO_CHECK_INFOS
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
