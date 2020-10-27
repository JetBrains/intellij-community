android {
  sourceSets {
    getByName("main") {
      java {
        srcDir("javaSource1")
        srcDirs("javaSource2", "javaSource3")
        include("javaInclude1")
        include("javaInclude2", "javaInclude3")
        exclude("javaExclude1", "javaExclude2", "javaExclude3")
      }
      jni {
        setSrcDirs(listOf("jniSource1", "jniSource2", "jniSource3"))
        include("jniInclude1", "jniInclude2", "jniInclude3")
        exclude("jniExclude1")
        exclude("jniExclude2", "jniExclude3")
      }
    }
  }
}
