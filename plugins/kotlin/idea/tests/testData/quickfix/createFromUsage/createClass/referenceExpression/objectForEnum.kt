// "Create object 'RED'" "true"
enum class SampleEnum {}

fun usage() {
    SampleEnum.RED<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix