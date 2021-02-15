dependencies {
  androidTestCompile("com.android.support.test.espresso:espresso-contrib:2.2.2") {
    exclude(mapOf("group" to "com.android.support", "module" to "support-v4"))
    exclude(mapOf("group" to "com.android.support", "module" to "support-annotations"))
    exclude(mapOf("group" to "com.android.support", "module" to "recyclerview-v7"))
    exclude(mapOf("group" to "com.android.support", "module" to "design"))
  }
  runtime(mapOf("group" to "org.gradle.test.classifiers", "name" to "service", "version" to "1.0", "classifier" to "jdk14", "ext" to "jar"))
}
