plugins {
    id("java")
}
group = "org.example"
version = "1.0-SNAPSHOT"
repositories {
    mavenCentral()
}
tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
dependencies {
}