// "Convert to a full name-based destructuring form" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

data class Person(val name: String, val age: Int) {
    operator fun component3(): String = name + age
}

fun test() {
    val (<caret>name, age, rendered) = Person("John", 30)
    print(rendered)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix