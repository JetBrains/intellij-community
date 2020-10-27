dependencies {
  compile(fileTree("libs"))
  compile(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"), "exclude" to listOf("*.aar"))))
  implementation(fileTree("libz"))
  implementation(fileTree(mapOf("dir" to "libz2", "include" to listOf("*.jar"), "exclude" to listOf("*.aar"))))
}
