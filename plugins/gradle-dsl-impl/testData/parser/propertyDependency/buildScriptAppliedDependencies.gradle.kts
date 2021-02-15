buildscript {
  apply(from="versions.gradle.kts")
  dependencies {
    classpath(extra["deps"]["android_gradle_plugin"])
  }
}

dependencies {
  compile(extra["deps"]["android_gradle_plugin"])
}
