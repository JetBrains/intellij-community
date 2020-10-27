dependencies {
  implementation(mapOf("group" to "my.test.dep", "name" to "artifact", "version" to "version")) {
    exclude(mapOf("module" to "module1"))
    exclude(mapOf("module" to "module2"))
  }
}