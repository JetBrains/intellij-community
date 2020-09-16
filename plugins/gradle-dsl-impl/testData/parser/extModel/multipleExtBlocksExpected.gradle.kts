apply(plugin =  "com.android.application")
val var1 by extra("1.5")
extra["newProp"] = true
android {
}
dependencies {
  compile(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
