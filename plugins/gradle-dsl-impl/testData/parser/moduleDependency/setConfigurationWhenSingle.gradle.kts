dependencies {
  test(project(":abc"))
  compile(project(":xyz"))
  api(project(mapOf("path" to ":klm")))
  testCompile(project(":")) {
    exclude(mapOf("module" to "module1"))
  }
}