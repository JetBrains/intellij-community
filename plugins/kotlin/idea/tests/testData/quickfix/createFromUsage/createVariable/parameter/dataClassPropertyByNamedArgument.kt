// "Create property 'parameter4' as constructor parameter" "true"

data class MyDto(
        val parameter1:String,
        val parameter2:String ="",
        val parameter3:String =""
)

fun main(args: Array<String>) {
    println(MyDto(parameter1 = "value1", <caret>parameter4 = "value4"))
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: val parameter4: kotlin.String