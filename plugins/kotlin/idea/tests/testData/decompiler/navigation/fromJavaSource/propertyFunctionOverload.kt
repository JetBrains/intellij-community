package testData.libraries

class MyTest {

  fun newBuilder(): Builder = Builder()

  class Builder {
    var cache: String? = null
    fun cache(cache: String?) {}

    var m: String? = null
    fun m(): String? = null

      var n: String? = null
      @JvmName("getN")
      fun n(s: String): String? = null

      @get:JvmName("o")
      var o: String? = null
      fun o(s: String): String? = null
  }
}
