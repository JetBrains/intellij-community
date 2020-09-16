dependencies {
  testCompile(project(":abc"), project(mapOf("path" to ":xyz")))
  compile(project(":klm"), project(":"), project(mapOf("path" to ":pqr", "configuration" to "config")))
}