plugins {
    kotlin("jvm")
}

tasks.register("prepareKotlinIdeaImport") {
    doLast {
        file("prepareKotlinIdeaImport.executed").writeText("OK")
    }
}