apply(plugin = "com.android.application")
val var1 by extra("1.5")
android {
}
dependencies {
  compile(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}