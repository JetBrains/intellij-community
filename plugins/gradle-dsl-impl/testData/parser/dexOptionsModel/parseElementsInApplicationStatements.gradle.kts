android {
  dexOptions {
    additionalParameters(listOf("abcd", "efgh"))
    setJavaMaxHeapSize("2048m")
    setJumboMode(true)
    setKeepRuntimeAnnotatedClasses(false)
    setMaxProcessCount(10)
    setOptimize(true)
    setPreDexLibraries(false)
    setThreadCount(5)
  }
}
