dependencies {
  compile(project(mapOf("path" to ":androidlib1", "configuration" to "flavor1Release")), project(":other"))
}