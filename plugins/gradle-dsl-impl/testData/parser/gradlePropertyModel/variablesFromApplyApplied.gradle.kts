val var1 = "Hello"
val var2 = var1
val var3 = true

val prop1 by extra(var2)
val prop2 by extra("${prop1} world!")
val prop3 by extra(var3)
