// "Create function 'checkProperty'" "true"
internal object model {
    val layer = ""
}

fun main(args: Array<String>) {
    <caret>checkProperty(model.layer::class)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix