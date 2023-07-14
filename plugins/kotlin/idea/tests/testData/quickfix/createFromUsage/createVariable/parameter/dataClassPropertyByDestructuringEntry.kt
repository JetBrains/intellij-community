// "Create property 'address' as constructor parameter" "true"
data class Person(val name: String, val age: Int)

fun person(): Person = TODO()

fun main(args: Array<String>) {
    val (name, age, address) = <caret>person()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix