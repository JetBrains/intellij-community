val version by extra("3.6")
val name by extra("guava")

dependencies {
  testCompile("org.gradle.test.classifiers:service:1.0")
  testCompile("com.google.guava:guava:+")
}
