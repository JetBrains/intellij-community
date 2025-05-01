class X(
    val propertyOne: Int,
    parameterOne: Int,

    val propertyTwo: Int = <caret>,

    val propertyThird: Int,
    parameterThird: Int,
)

// EXIST: { lookupString: "propertyOne", typeText: "Int", module: "light_idea_test_case", icon: "Parameter", attributes: "", allLookupStrings: "propertyOne", itemText: "propertyOne" }
// EXIST: { lookupString: "parameterOne", typeText: "Int", module: "light_idea_test_case", icon: "Parameter", attributes: "", allLookupStrings: "parameterOne", itemText: "parameterOne" }

// ABSENT: propertyThird
// ABSENT: parameterThird
