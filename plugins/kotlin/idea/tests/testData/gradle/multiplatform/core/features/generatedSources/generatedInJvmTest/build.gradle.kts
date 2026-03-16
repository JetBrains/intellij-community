plugins {
    kotlin("multiplatform")
}

group = "com.example"
version = "1.0"

kotlin {
    jvm()
    linuxX64()
}

val generatorTask = project.tasks.register("generator") {
    val outputDirectory = project.layout.projectDirectory.dir("src/jvmTest/kotlinGen")
    outputs.dir(outputDirectory)
    doLast {
        outputDirectory.file("generatedCommon.kt").asFile.writeText(
            //language=kotlin
            """
            fun printHello() {
                println("hello")
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.getByName("jvmTest").generatedKotlin.srcDir(generatorTask)
