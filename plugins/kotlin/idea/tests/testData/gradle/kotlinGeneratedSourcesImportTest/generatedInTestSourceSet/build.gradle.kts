plugins {
    kotlin("jvm")
}

val generatorTask = project.tasks.register("generator") {
    val outputDirectory = project.layout.projectDirectory.dir("src/test/kotlinGen")
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

kotlin.sourceSets.getByName("test").generatedKotlin.srcDir(generatorTask)