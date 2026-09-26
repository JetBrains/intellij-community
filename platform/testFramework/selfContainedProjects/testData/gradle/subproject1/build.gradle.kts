plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
}