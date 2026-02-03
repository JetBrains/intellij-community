plugins {
    kotlin("jvm")
}

val integrationTestCompilation = kotlin.target.compilations.create("integrationTest") {
    associateWith(kotlin.target.compilations.getByName("main"))
}

tasks.register<Test>("integrationTest") {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Runs functional tests"
    testClassesDirs = integrationTestCompilation.output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
}

val integrationTestImplementation = configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.getByName("implementation"))
    extendsFrom(configurations.getByName("testImplementation"))
}

dependencies {
    integrationTestImplementation(kotlin("test-junit"))
}