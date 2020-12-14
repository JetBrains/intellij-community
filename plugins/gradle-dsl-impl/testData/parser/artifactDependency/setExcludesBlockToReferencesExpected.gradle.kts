val junit_version by extra("2.3.1")
val excludes_name by extra("bad")
val excludes_group by extra("dependency")
dependencies {
  implementation("junit:junit:$junit_version") {
    exclude(mapOf("group" to "$excludes_group", "module" to "$excludes_name"))
  }
}
