plugins {
  `kotlin-dsl`
}

dependencies {
  val libs = project.extensions.getByType<VersionCatalogsExtension>().find("libs").get()
  implementation(libs.findLibrary("kotlin-gradle-plugin").get())
  testImplementation(kotlin("test"))
}

gradlePlugin {
  plugins {
    create("multiplatform") {
      id = "multiplatform"
      implementationClass = "convention.multiplatform.MultiplatformPlugin"
      description = "Multiplatform Plugin with target selection"
    }
  }
}
