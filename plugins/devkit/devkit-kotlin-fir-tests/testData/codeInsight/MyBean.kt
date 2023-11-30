package p

import com.intellij.util.xmlb.annotations.Attribute

class MyBean {
  @Attribute("default")
  @JvmField
  var defaultValue: String = ""
}