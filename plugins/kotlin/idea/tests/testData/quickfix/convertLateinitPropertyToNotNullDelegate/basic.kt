// "Convert to notNull delegate" "true"
// WITH_STDLIB
<caret>lateinit var x: Boolean
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertLateinitPropertyToNotNullDelegateFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertLateinitPropertyToNotNullDelegateFixFactory$ConvertLateinitPropertyToNotNullDelegateFix