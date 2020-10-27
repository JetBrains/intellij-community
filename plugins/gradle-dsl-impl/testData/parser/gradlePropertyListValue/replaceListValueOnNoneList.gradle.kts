val prop1 by extra(mapOf("key1" to "value1", "key2" to false, "key3" to 17))
extra["prop2"] = "hello"
val prop3 by extra(prop1) // Should only work for resolved properties.
