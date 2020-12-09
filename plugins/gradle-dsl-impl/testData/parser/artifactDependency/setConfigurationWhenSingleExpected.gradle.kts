dependencies {
  val version by extra("2.0")
  val sampleDep by extra("a:b:1.0")
  "androidTest"("org.gradle.test.classifiers:service:1.0:jdk15@jar")
  api(sampleDep)
  compile(sampleDep)
  testImplementation("org.hibernate:hibernate:3.1") {
    force=true
  }
  debugImplementation(group="com.example", name="artifact", version="1.0")
  implementation(group="org.example", name="artifact", version="2.0")
  "release"(mapOf("group" to "org.example","name" to "artifact","version" to "$version")) {
    force=true
  }
}
