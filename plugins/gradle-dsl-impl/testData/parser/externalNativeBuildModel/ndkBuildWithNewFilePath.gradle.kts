android {
  externalNativeBuild {
    ndkBuild {
      setPath(File("foo", "Android.mk"))
    }
  }
}