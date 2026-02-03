class X(
    val property: Int = <caret>,
) {
    companion object {
        fun companionFun(): Int = 10
        val companionProperty: Int = 10
    }
}

// EXIST: { lookupString: "companionFun", tailText: "()", typeText: "Int", module: "light_idea_test_case", icon: "Method", attributes: "bold", allLookupStrings: "companionFun", itemText: "companionFun" }
// EXIST: { lookupString: "companionProperty", typeText: "Int", module: "light_idea_test_case", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg", attributes: "bold", allLookupStrings: "companionProperty, getCompanionProperty", itemText: "companionProperty" }
