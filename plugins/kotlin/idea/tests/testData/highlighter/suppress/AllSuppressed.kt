@file:Suppress("unused", "MoveVariableDeclarationIntoWhen")

fun a(i: Int) {
    @Suppress("MoveVariableDeclarationIntoWhen") val suppressed = 4
    when (suppressed) {
        i -> Unit
        else -> Unit
    }
}

fun b() {
    @Suppress("MoveVariableDeclarationIntoWhen") val unsuppressed = 4
    doSmth(unsuppressed)
    c()
}

fun doSmth(<warning descr="[UNUSED_PARAMETER] Parameter 'i' is never used">i</warning>: Int) = Unit

@Suppress("unused", "MoveVariableDeclarationIntoWhen")
fun c() = Unit

// NO_CHECK_INFOS
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
