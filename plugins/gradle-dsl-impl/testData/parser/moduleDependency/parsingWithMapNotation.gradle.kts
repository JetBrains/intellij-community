dependencies {
  compile(project(mapOf("path" to ":androidlib1", "configuration" to "flavor1Release")))
  compile(project(mapOf("path" to ":androidlib2", "configuration" to "flavor2Release")))
  runtime(project(mapOf("path" to ":javalib2")))
}