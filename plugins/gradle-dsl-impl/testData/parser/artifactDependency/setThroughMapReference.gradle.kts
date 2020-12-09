val version by extra(1.0)
val dep by extra(mapOf("name" to "awesome", "group" to "some", "version" to "$version"))
dependencies {
  compile(dep)
}
