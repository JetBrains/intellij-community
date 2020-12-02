val service by extra("org.gradle.test.classifiers:service:1.0")
val guavaPart by extra("google.guava:guava:+")

dependencies {
  testCompile("some:gradle:thing")
  testCompile("some:other:gradle:thing")
}
