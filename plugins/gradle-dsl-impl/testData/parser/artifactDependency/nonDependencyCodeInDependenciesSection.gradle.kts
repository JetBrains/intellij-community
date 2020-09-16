dependencies {
  compile("com.android.support:appcompat-v7:22.1.1")
  runtime(group="com.google.guava", name="guava", version="18.0")
  apply(plugin="com.test.xyz")
  testCompile("org.hibernate:hibernate:3.1") {
    isForce = true
  }
}
