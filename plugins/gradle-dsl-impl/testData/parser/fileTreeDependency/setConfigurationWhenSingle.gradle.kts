dependencies {
  test(fileTree("libs"))
  compile(fileTree(mapOf("dir" to "xyz")))
  api(fileTree("klm"))
  testCompile(fileTree(mapOf("dir" to "a", "include" to listOf("*.jar")))) {
    exclude(mapOf("module" to "module1"))
  }
}
