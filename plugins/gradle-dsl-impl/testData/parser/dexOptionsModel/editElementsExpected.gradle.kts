android {
  dexOptions {
    additionalParameters = listOf("abcd", "xyz")
    javaMaxHeapSize = "1024m"
    jumboMode = false
    keepRuntimeAnnotatedClasses = true
    maxProcessCount = 5
    optimize = false
    preDexLibraries = true
    threadCount = 10
  }
}
