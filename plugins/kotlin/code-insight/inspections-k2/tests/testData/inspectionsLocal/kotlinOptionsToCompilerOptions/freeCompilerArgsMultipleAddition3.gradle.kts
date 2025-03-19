// FIX: Replace 'kotlinOptions' with 'compilerOptions'
// DISABLE_K2_ERRORS
// TODO: KTIJ-32773
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
            freeCompilerArgs = freeCompilerArgs +
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi" +
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi" +
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api" +
                "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi"
        }
    }
}
