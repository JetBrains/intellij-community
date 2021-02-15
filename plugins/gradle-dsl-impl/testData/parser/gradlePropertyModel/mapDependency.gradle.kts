val prop1 by extra(mapOf("key" to "value"))
val prop2 by extra("${prop1["key"]}")
