val prop by extra(true)
val prop1 = mapOf("key1" to "value${prop}")
val prop2 = mapOf("key2" to prop1)
val prop3 = "${prop2["key2"]["key1"]}"
