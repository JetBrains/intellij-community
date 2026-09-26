package buildSrc.convention

import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    kotlin("multiplatform")
    id("buildSrc.convention.base")
}

kotlin {
    jvm()
    macosX64()
    linuxX64()


    targets.withType<KotlinJvmTarget>().configureEach {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        nativeTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
            }
        }
    }
}


tasks.register("helloFromMyPlugin") {
    group = "kmp-plugin"
    description = "Prints a greeting from MyKmpPlugin"
    doLast {
        println("Hello from MyKmpPlugin in project '${project.name}'!")
    }
}
