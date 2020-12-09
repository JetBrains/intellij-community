android {
  dexOptions {
    additionalParameters = listOf("ijkl", "mnop")
    javaMaxHeapSize = "1024m"
    jumboMode = false
    keepRuntimeAnnotatedClasses = true
    maxProcessCount = 5
    optimize = false
    preDexLibraries = true
    threadCount = 10
  }
}
