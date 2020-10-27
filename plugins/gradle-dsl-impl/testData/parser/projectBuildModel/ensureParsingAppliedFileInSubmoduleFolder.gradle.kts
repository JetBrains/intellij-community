buildscript {
  apply(from="<SUB_MODULE_NAME>/a.gradle.kts")

  dependencies {
    classpath("com.android.tools.build:gradle:${rootProject.extra["version"]}")
  }
}
