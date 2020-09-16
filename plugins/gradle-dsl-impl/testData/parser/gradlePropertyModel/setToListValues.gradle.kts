val var1 = "hello"
val prop1 by extra(5)
val prop2 by extra(var1)
val prop3 by extra("${prop2}")
val prop4 by extra(mapOf("key" to "val", "key1" to true))
val prop5 by extra(listOf("val"))
val prop6 by extra(mapOf("key" to "val"))