apply(from="../b.gradle.kts")

val childProperty by extra(extra["greeting"] as String)
