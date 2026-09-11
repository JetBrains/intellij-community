// "Safe delete 'name'" "true"
import java.io.File

val File.<caret>name: String
    get() = getName()
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix