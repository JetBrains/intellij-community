// "Create property 'x' as constructor parameter" "true"
annotation class Annotation

@Annotation(<caret>x = 1)
class C
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix