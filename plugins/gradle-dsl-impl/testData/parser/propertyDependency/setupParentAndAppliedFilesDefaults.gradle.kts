var signing = mapOf("storeF" to "store.txt", "storeP" to listOf("fed", "grt"), "keyF" to "key.file", "keyP" to listOf("cfv", "bnn"))

val vars by extra(mapOf("minSdk" to 17, "maxSdk" to (extra["vars"] as Map<*,*>)["minSdk"], "signing" to signing))
