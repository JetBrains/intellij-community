plugins {
    id("org.jetbrains.intellij") version "1.5.3"
}

group = "ru.adelf"
version = "2022.2"

repositories {
    mavenCentral()
}

apply {
    plugin("idea")
    plugin("org.jetbrains.intellij")
    plugin("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
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
    version.set("IU-222.2270.31-EAP-SNAPSHOT")
    plugins.set(
        listOf(
            "com.jetbrains.php:222.2270.31",
            "yaml",
            "org.jetbrains.plugins.go:222.2270.31",
            "Docker",
            "pythonid:222.2270.35",
            "org.jetbrains.plugins.ruby:222.2270.31",
            "Kotlin",
            "coverage",
            "CSS",
            "java-i18n",
            "properties",
            "java"
        )
    )
    pluginName.set(".env files support")
}

tasks {
    patchPluginXml {
        sinceBuild.set("212")
        untilBuild.set("222.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runPluginVerifier {
        ideVersions.set(listOf("IU-213.3714.440"))
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}