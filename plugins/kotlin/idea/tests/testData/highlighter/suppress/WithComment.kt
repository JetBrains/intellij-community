// MoveVariableDeclarationIntoWhen
@Suppress("<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>", "unused")
class C

// MoveVariableDeclarationIntoWhen
@Suppress("unused", "<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>")
fun f() = 1

val v: Int
    // MoveVariableDeclarationIntoWhen
    @Suppress("<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>")
    get() = 1

@Ann("MoveVariableDeclarationIntoWhen")
@Suppress("<warning descr="Redundant suppression">MoveVariableDeclarationIntoWhen</warning>")
class C2

annotation class Ann(val s: String)

// NO_CHECK_INFOS
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
