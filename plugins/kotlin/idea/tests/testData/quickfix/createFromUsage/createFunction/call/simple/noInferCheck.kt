// "Create function 'process'" "true"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// IGNORE_K1
fun test(objects: List<Any>) {
    val strings = objects.filterIsInstance<String>()
    for (string in strings) {
        <caret>process(string) // must not create function with parameter annotated @NoInfer
    }
}