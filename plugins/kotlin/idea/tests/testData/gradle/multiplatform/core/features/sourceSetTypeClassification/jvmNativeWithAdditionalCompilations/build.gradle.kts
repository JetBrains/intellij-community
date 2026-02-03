import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

plugins {
    kotlin("multiplatform")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    fun KotlinTarget.configureAdditionalCompilations() {
        val main by compilations.getting
        val additionalTest by compilations.creating {
            associateWith(main)
        }
        val additionalNonTest by compilations.creating
    }

    jvm {
        configureAdditionalCompilations()
    }
    linuxX64 {
        configureAdditionalCompilations()
    }
}
