val version by extra(1.0)
val dep by extra(mapOf("name" to "boo", "group" to "spooky", "version" to "2.0"))
dependencies {
  compile(dep)
}
