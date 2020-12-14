val service by extra("org.gradle.test.classifiers:service:1.0")
val guavaPart by extra("google.guava:guava:+")

dependencies {
  testCompile(service)
  testCompile("com.${guavaPart}")
}
