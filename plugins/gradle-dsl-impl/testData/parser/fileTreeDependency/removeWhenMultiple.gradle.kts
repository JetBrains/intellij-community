dependencies {
  compile(fileTree("xyz"))
  compile(fileTree(mapOf("dir" to "libs", "include" to "*.jar")))
  compile(fileTree("abc"))
}
