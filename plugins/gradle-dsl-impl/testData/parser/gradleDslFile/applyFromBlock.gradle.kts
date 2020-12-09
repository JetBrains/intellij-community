buildscript {
  apply(from = "a.gradle.kts")
  dependencies {
    classpath(extra["prop"])
  }
}
