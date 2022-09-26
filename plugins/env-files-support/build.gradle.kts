plugins {
    id("org.jetbrains.intellij") version "1.9.0"
}

group = "ru.adelf"
version = "2022.3"

repositories {
    mavenCentral()
}

apply {
    plugin("idea")
    plugin("org.jetbrains.intellij")
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

intellij {
    version.set("IU-223.4884.69-EAP-SNAPSHOT")
    plugins.set(
        listOf(
            "com.jetbrains.php:223.4884.75",
            "yaml",
            "org.jetbrains.plugins.go:223.4884.65",
            "Docker",
            "pythonid:223.4884.69",
            "org.jetbrains.plugins.ruby:223.4884.69",
            "Kotlin",
            "java-i18n",
            "properties",
            "java"
        )
    )
    pluginName.set(".env files support")
}

tasks {
    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("223.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runPluginVerifier {
        ideVersions.set(listOf("IU-223.4884.69"))
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}