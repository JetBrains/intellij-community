dependencies {
  api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
  implementation("com.example.libs:lib1:0.+")
  api("com.android.support:appcompat-v7:+")
  compile(files("lib1.jar"))
  compile(files("lib2.jar", "lib3.aar"))
  implementation(files("lib4.aar"))
  debugImplementation(project(":javalib1"))
  releaseImplementation("some:lib:1.0")
  releaseImplementation(files("lib5.jar"))
  releaseImplementation(project(":lib3"))
  releaseImplementation(fileTree(mapOf("dir" to "libz", "include" to listOf("*.jar"))))
  releaseImplementation(mapOf("group" to "org.springframework", "name" to "spring-core", "version" to  "2.5"))
}
