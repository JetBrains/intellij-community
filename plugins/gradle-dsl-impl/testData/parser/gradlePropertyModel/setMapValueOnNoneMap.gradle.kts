val prop1 by extra(listOf("value1", false, 17))
val prop2 by extra("hello")
val prop3 by extra(prop1) // Should only work for resolved properties.