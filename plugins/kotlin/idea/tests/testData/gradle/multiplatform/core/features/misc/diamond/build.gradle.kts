buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

plugins {
    kotlin("multiplatform").version("{{kgp_version}}")
}

group = "project"
version = "1.0"

kotlin {
    jvm() 
    js(IR)

    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmAndJsMain by creating {
            dependsOn(commonMain)
        }

        val jvmAndJsTest by creating {
            dependsOn(commonTest)
        }

        val linuxAndJsMain by creating {
            dependsOn(commonMain)
        }
        
        val linuxAndJsTest by creating {
            dependsOn(commonTest)
        }

        jvm().compilations["main"].defaultSourceSet {
            dependsOn(jvmAndJsMain)
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }

        jvm().compilations["test"].defaultSourceSet {
            dependsOn(jvmAndJsTest)
        }

        js().compilations["main"].defaultSourceSet {
            dependsOn(jvmAndJsMain)
            dependsOn(linuxAndJsMain)
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }

        js().compilations["test"].defaultSourceSet {
            dependsOn(jvmAndJsTest)
            dependsOn(linuxAndJsTest)
        }

        linuxX64().compilations["main"].defaultSourceSet {
            dependsOn(linuxAndJsMain)
        }

        linuxX64().compilations["test"].defaultSourceSet {
            dependsOn(linuxAndJsTest)
        }
    }
}
