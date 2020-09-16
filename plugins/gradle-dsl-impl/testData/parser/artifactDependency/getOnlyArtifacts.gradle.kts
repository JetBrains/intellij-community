dependencies {
  compile("com.android.support:appcompat-v7:22.1.1")
  compile(mapOf("name" to "guava", "group" to "com.google.guava", "version" to "18.0"))
  compile(project(":javaLib"))
  compile(fileTree("libs"))
  compile(files("lib.jar"))
}
