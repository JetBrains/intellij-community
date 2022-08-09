// Disabling warning The `kotlin-dsl` plugin applied to project ':buildSrc' enables experimental Kotlin compiler features
kotlinDslPluginOptions.experimentalWarning.set(false)

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}
