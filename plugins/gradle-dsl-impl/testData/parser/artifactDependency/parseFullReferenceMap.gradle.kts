val dependency by extra(mapOf("group" to "group", "name" to "name", "version" to "1.0"))
val guavaGroup by extra("com.google.guava")
val guavaName by extra("guava")
val otherDependency by extra(mapOf("group" to "g", "name" to "n", "version" to "2.0"))

dependencies {
  testCompile(dependency)
  compile(group=guavaGroup, name=guavaName, version="4.0") {}
  testCompile(otherDependency) {}
  compile(group=guavaName, name=guavaGroup, version="3.0")
}
