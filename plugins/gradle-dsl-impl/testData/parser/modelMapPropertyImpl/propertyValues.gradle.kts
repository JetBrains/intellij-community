// TODO(b/72940492): Replace propC1 and propRef1 with propC and propRef respectively.
val propB by extra("2")
val propC by extra("3")
val propRef by extra(propB)
val propInterpolated by extra("${propB}nd")
val propMap by extra(mapOf("one" to "1", "B" to propB, "propC1" to propC, "propRef1" to propRef, "interpolated" to propInterpolated))
val propMapRef by extra(propMap)
