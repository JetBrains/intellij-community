plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

subprojects {
  repositories {
    mavenCentral()
  }
}