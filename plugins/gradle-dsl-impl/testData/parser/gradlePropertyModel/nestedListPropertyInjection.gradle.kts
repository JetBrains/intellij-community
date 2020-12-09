val prop1 by extra(listOf(1, 2, 3))
val prop2 by extra(listOf(prop1, prop1, prop1))
val prop3 by extra(mapOf("key" to prop2))
val prop4 by extra("${prop3["key"][0][2]}")
