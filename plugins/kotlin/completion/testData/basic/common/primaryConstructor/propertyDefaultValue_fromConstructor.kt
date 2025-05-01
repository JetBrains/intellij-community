// IGNORE_K1

class X(
    val propertyOne: Int,
    parameterOne: Int,

    val propertyTwo: Int = <caret>,

    val propertyThird: Int,
    parameterThird: Int,
)

// EXIST: { lookupString: "propertyOne", typeText: "Int", module: "light_idea_test_case", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg", attributes: "bold", allLookupStrings: "getPropertyOne, propertyOne", itemText: "propertyOne" }
// ABSENT: parameterOne

// EXIST: propertyThird
// ABSENT: parameterThird
