import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "ru.adelf"
version = "2024.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

apply {
    plugin("idea")
    plugin("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/gen")
        resources.srcDirs("src/main/resources")
    }
    test {
        java.srcDirs("src/test/java")
        resources.srcDirs("src/test/resources")
    }
}

dependencies {
    intellijPlatform {
        create("IU", "2023.2.4", useInstaller = true)

        plugin("com.jetbrains.php", "232.8660.153")
        plugin("org.jetbrains.plugins.go", "232.8660.48")
        plugin("org.jetbrains.plugins.ruby", "232.8660.142")
        plugin("PythonCore", "232.8660.142")

        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
        bundledPlugin("Docker")
        bundledPlugin("org.jetbrains.plugins.yaml")

        pluginVerifier()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    testRuntimeOnly("com.fasterxml.jackson.core:jackson-databind:2.14.1")
}

intellijPlatform {
    pluginConfiguration {
        name = ".env files support"

        ideaVersion {
            sinceBuild = "232"
            untilBuild = "242.*"
        }
    }

    pluginVerification {
        ides {
            ide("IU", "2023.2.4")
        }

        freeArgs = listOf("-mute", "TemplateWordInPluginName")
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
