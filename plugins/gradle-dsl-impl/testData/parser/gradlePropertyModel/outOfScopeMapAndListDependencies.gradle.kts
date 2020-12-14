val prop1 = "value1"
val prop2 = mapOf("key" to "value2")
val prop3 = listOf("value3")
val prop4 by extra("${prop1} and ${prop2["key"]} and ${prop3[0]}")
