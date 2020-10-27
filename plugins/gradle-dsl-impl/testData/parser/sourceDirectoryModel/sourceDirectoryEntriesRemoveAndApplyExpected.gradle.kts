android {
  sourceSets {
    getByName("main") {
      java {
        srcDir("javaSource1")
        include("javaInclude1")
        exclude("javaExclude1")
      }
      jni {
        setSrcDirs(listOf("jniSource1"))
        include("jniInclude1")
        exclude("jniExclude1")
      }
    }
  }
}
