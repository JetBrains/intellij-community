// "Create property 'y' as constructor parameter" "true"
annotation class Annotation(val x: Int)

@Annotation(x = 1, <caret>y = 2)
class C
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix