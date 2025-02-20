// "Import class 'Language'" "true"
// ERROR: No value passed for parameter 'value'
// ERROR: Property must be initialized
// ERROR: Unresolved reference: Language33
// K2_AFTER_ERROR: No value passed for parameter 'value'.
// K2_AFTER_ERROR: Property must be initialized.
// K2_AFTER_ERROR: Unresolved reference 'Language33'.

import org.intellij.lang.annotations.JdkConstants

@Language<caret>
@Language33
val v: JdkConstants
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix