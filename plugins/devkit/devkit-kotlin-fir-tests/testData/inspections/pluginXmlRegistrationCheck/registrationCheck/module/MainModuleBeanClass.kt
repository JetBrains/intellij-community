import com.intellij.util.xmlb.annotations.Attribute

class MainModuleBeanClass {
  @JvmField
  @Attribute("implementationClass")
  var implementationClass: String? = null
}
