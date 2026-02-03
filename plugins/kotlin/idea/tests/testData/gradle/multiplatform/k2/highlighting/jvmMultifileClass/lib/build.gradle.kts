plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

kotlin {
    jvm()
    js()

    sourceSets {
        val intermediate by creating {
            dependsOn(commonMain.get())
        }
        val jvmMain by getting {
            dependsOn(intermediate)
        }
        val jsMain by getting {
            dependsOn(intermediate)
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

publishing {
    repositories {
        maven(rootProject.layout.projectDirectory.dir("repo"))
    }
}
