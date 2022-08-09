import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.plugin.structure.intellij.version.IdeVersionImpl
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.ajoberstar.grgit.Credentials
import org.ajoberstar.grgit.operation.CloneOp
import org.jetbrains.completion.full.line.gradle.Utils
import org.jetbrains.completion.full.line.gradle.Plugins

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS
Utils.init(project)

val teamcity: Map<String, String> by project
val platformVersion: String by project

@Suppress("PropertyName")
val isCI = project.hasProperty("teamcity")

group = "org.jetbrains.completion.full.line"
version = "0.2." + (if (isCI) teamcity["build.number"] else "SNAPSHOT") + "-$platformVersion"

plugins {
    java
    // Version for current target IDEA
    // Better not to change to prevent from different Kotlin versions in same project
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.changelog")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.ajoberstar.grgit") version "1.7.2"
}

val language: String by project
val junit: String by project

repositories {
    maven {
        url = uri("https://packages.jetbrains.team/maven/p/ml/intellij-ml")

        credentials {
            // TODO: temporary creds, while completion-engine is not in artifactory
            username = "kirill.krylov"
            password =
                "eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiIza3pBRGo0TFNWc0YiLCJhdWQiOiJjaXJjbGV0LXdlYi11aSIsIm9yZ0RvbWFpbiI6ImpldGJyYWlucyIsIm5hbWUiOiJLaXJpbGwuS3J5bG92IiwiaXNzIjoiaHR0cHM6XC9cL2pldGJyYWlucy50ZWFtIiwicGVybV90b2tlbiI6ImR3bUViMXh5V2h3IiwicHJpbmNpcGFsX3R5cGUiOiJVU0VSIiwiaWF0IjoxNjE3MDk1Nzk4fQ.ay_Vegla1sj5okWIjViKjP9U9oeKRKxNIZ9y4fUrdwD75_NkyfmcVduUXf-TK2CNadUmqwlppmE3mG8OYvoY9kfj0SdVYgxvL4xvkFrBDQiYbyTxzAXjsXfgBijZ4a_Iy9Sltk83S6Jgms4a_UK9imvpileJYsCXIM6dI5BItEI"
        }
    }
}


dependencies {
    implementation(project(":languages:common"))
    implementation(project(":languages:java"))
    implementation(project(":languages:kotlin"))
    implementation(project(":languages:python"))
    implementation(project(":languages:js"))

    implementation("org.jetbrains.completion.full.line:markers")

    implementation(kotlin("reflect"))
    implementation("ml.intellij:completion-engine:0.0.17")
    //  Conflicts with 1.0.0-RC kotlin serialization module in ideaIU
    //  implementation("io.github.pdvrieze.xmlutil:core:0.82.0")
    implementation("net.devrieze:xmlutil-serialization-jvm:0.80.0-RC")
    implementation("org.rauschig:jarchivelib:1.1.0")
    implementation("org.apache.logging.log4j:log4j-core:2.17.0")
}

changelog {
    path.set("${project.projectDir}/CHANGELOG.md")
    header.set("{0}")
    version.set(project.version.toString().removeSuffix("-$platformVersion"))
}

allprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("org.jetbrains.intellij")
        plugin("kotlinx-serialization")
    }

    repositories {
        jcenter()
        mavenCentral()
        maven(url = "https://www.jetbrains.com/intellij-repository/snapshots")
        maven(url = "https://www.jetbrains.com/intellij-repository/releases")
//        Uncomment if xmlutil will be used from io.github.pdvrieze
//        maven(url = "https://s01.oss.sonatype.org/content/repositories/releases/")
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("org.apache.logging.log4j:log4j-core:2.17.0")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junit}")
        testImplementation("org.junit.jupiter:junit-jupiter-api:${junit}")
        testImplementation("org.junit.jupiter:junit-jupiter-params:${junit}")
        testImplementation("org.junit.vintage:junit-vintage-engine:${junit}")
        testImplementation("io.mockk:mockk:1.10.0")
    }


    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks {
        test {
            useJUnitPlatform()
        }

        withType<KotlinCompile>().all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_11.toString()
                languageVersion = "1.4"
                apiVersion = "1.4"
                freeCompilerArgs = listOf("-Xjvm-default=enable")
            }
        }
    }

    intellij {
        type.set(Utils.get().ideaVersion.type)
        version.set(Utils.get().ideaVersion.target)
    }
}

subprojects {
    sourceSets {
        main {
            java.srcDirs("src")
            resources.srcDirs("resources")
        }
        test {
            java.srcDirs("test")
            resources.srcDirs("testResources")
        }
    }
}

intellij {
    type.set(Utils.get().ideaVersion.type)
    version.set(Utils.get().ideaVersion.target)
    pluginName.set("full-line")

    // `java` plugin is required, and the rest of the plugins are language dependent
    val plugins = mutableListOf(Plugins.java)
    if (version.get().take(3).toInt() >= 203) {
        plugins.add(Plugins.mlRanking)
    }

    when (language) {
        "java"   -> run {}
        "kotlin" -> plugins.add(Plugins.kotlin)
        "python" -> plugins.add(Plugins.python)
        "js"     -> plugins.add(Plugins.js)
        "all"    -> {
            plugins.add(Plugins.js)
            plugins.add(Plugins.python)
            plugins.add(Plugins.kotlin)
        }
        else     -> throw InvalidUserDataException("Wrong language: `${language}`, pick one of supported (settings.gradle.kts.kts)")
    }

    this.plugins.set(plugins)
}

tasks {
    val patchVersion = task("patchVersion") {
        group = "flcc"
        doLast {
            project.convention.getPlugin<JavaPluginConvention>()
                .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .resources.srcDirs.forEach { resources ->
                    val root = File(resources, "META-INF")

                    val versionInPluginRegex = Regex("[0-9]+")
                    val pluginRegex = Regex("plugin\\.($versionInPluginRegex\\.)?xml")
                    val files = root.listFiles { file -> pluginRegex.matches(file.name) }!!.map { it.name }

                    val missingPluginXML = Utils.get().allPlatformVersions
                        .minus(files.mapNotNull { versionInPluginRegex.find(it)?.groups?.get(0)?.value })
                        .also { assert(it.size == 1) }
                        .let { it[0] }

                    if (missingPluginXML != platformVersion) {
                        logger.info("Changing plugin.xml $missingPluginXML with $platformVersion")
                        File(root, "plugin.xml").renameTo(File(root, "plugin.$missingPluginXML.xml"))
                        File(root, "plugin.$platformVersion.xml").renameTo(File(root, "plugin.xml"))
                    }

                    when (Utils.get().platformVersion) {
                        "202" -> Utils.get().project.tasks.withType<KotlinCompile>().all {
                            exclude("org/jetbrains/completion/full/line/platform/ranking/FullLineCompletionPolicy.kt")
                        }
                    }
                }
        }
    }

    matching { it != patchVersion }
        .all { dependsOn(patchVersion) }

    withType<PatchPluginXmlTask> {
        sinceBuild.set(Utils.get().ideaVersion.since)
        untilBuild.set(Utils.get().ideaVersion.until)
        changeNotes.set(provider {
            changelog.getAll()
                .map { (_, log) -> log.withHeader(true).toHTML() }
                .joinToString("")
        })
    }

    withType<RunIdeTask> {
        jvmArgs("-Xmx4G")
        // Disable tips of the day on start
        jvmArgs("-Dide.show.tips.on.startup.default.value=false")
        doFirst { Utils.get().disableSubprojectTask(name) }
    }

    withType<RunPluginVerifierTask> {
        enabled = isCI
        if (isCI) {
            localPaths.setFrom(teamcity["local.ide.path"])
            teamCityOutputFormat.set(true)
        }

        doFirst { Utils.get().disableSubprojectTask(name) }
    }

    task("verifyIdeaVersion") {
        group = "flcc"
        enabled = isCI
        doLast {
            val ide = teamcity.getValue("local.ide.build.number").toIdeVersion()
            val since = Utils.get().ideaVersion.since.toIdeVersion()
            val until = Utils.get().ideaVersion.until.toIdeVersion()

            if (ide !in since..until) {
                throw BuildException("IDE `$ide` is not compatible.", null)
            }
        }
    }

    val cloneMarkersTask = create("clone-markers") {
//        dependsOn("build")
        group = "flcc"
        doLast {
            val localPath = project.property("markers.local.path")?.toString()?.takeIf { it.isNotEmpty() }
            val gitBranch = project.property("markers.git.branch")?.toString()?.takeIf { it.isNotEmpty() }

            if (localPath != null) {
                logger.info("Using markers from local dir $localPath")
                copy {
                    from(project.property("markers.local.path"))
                    into("$buildDir/resources/test/testData/markers".also { if (File(it).exists()) File(it).deleteRecursively() })
                    include("**/**")
                }
            } else if (gitBranch != null) {
                logger.info("Using markers from local dir $localPath")
                CloneOp().apply {
                    dir = "$buildDir/resources/test/testData/markers".also { if (File(it).exists()) File(it).deleteRecursively() }
                    uri = project.property("markers.git.repo")?.toString()
                    refToCheckout = gitBranch.toString()
                    credentials = Credentials(
                        project.property("markers.creds.username")?.toString(),
                        project.property("markers.creds.password")?.toString()
                    )
                }.call()
            } else {
                throw GradleException("No git or local creds were provided")
            }
        }
    }

    test {
        subprojects.forEach { dependsOn(it.tasks.test) }
        useJUnitPlatform()

        exclude("**/*MarkerTestCase*")
    }

    create("test-markers-plugin", Test::class.java) {
        doFirst {
            File("build/resources/test/.flcc").takeIf { it.exists() }?.deleteRecursively()
        }
        group = "flcc"
        dependsOn(cloneMarkersTask)
        useJUnitPlatform()
        ignoreFailures = project.extra.properties["ignoreFailures"]?.toString()?.toBoolean() ?: false

        include("**/*MarkerTestCase*")
    }
}

fun String.toIdeVersion(): IdeVersion = IdeVersionImpl.fromString(this)
