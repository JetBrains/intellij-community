package library

open class OldKotlinClass {

  @JvmField
  var recentField: String = ""

  var recentProperty: Int = 0

  //recent constructor
  constructor() {
  }

  //recent constructor
  constructor(s: String) {
  }

  //old constructor
  constructor(x: Int) {
  }

  open fun recentMethod() {
  }

  companion object {
    @JvmStatic
    fun recentStaticMethod() {

    }
  }

}