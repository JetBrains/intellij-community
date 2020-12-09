apply(from = "a.gradle.kts")

val prop4 by extra(prop1[0])
val prop5 by extra(extra["prop2"] as Boolean)
