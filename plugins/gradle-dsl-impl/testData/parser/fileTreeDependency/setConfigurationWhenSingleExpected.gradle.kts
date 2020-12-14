dependencies {
  "androidTest"(fileTree("libs"))
  api(fileTree(mapOf("dir" to "xyz")))
  compile(fileTree("klm"))
  testImplementation(fileTree(mapOf("dir" to "a", "include" to listOf("*.jar")))) {
    exclude(mapOf("module" to "module1"))
  }
}
