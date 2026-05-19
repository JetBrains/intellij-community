// "Safe delete 'name'" "true"
import java.io.File

val File.<caret>name: String
    get() = getName()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$DeleteRedundantExtensionAction
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix