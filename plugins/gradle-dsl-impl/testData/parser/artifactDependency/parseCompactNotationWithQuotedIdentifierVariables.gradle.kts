val versions by extra(mapOf("one" to "1.0.0", "two" to "2.0.0", "three" to "3.0.0", "four" to "4.0.0", "five" to "5.0.0", "six" to "6.0.0"))

dependencies {
  implementation("com.google.a:a:${versions["one"]}")
  implementation("com.google.b:b:${`versions`["two"]}")
  implementation("com.google.c:c:${versions["""three"""]}")
  implementation("com.google.d:d:${`versions`["""four"""]}")
  implementation("com.google.e:e:${versions["f\u0069ve"]}")
  implementation("com.google.f:f:${`versions`["\u0073i\u0078"]}")
}
