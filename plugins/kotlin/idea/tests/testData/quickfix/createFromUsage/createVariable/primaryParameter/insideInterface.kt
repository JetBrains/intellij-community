// "Create property 'v2' as constructor parameter" "false"
// ERROR: Unresolved reference: v2
// K2_AFTER_ERROR: Unresolved reference 'v2'.

interface A

fun m(a: A){
  val p = a.v<caret>2
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction