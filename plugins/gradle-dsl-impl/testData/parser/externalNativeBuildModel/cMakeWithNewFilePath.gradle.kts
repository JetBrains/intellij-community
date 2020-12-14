android {
  externalNativeBuild {
    cmake {
      setPath(File("foo/bar"))
    }
  }
}
