android {
  productFlavors {
    create("x") {
      externalNativeBuild {
        cmake {
          arguments("-DANDROID_STL=c++_static")
        }
      }
    }
  }
}
