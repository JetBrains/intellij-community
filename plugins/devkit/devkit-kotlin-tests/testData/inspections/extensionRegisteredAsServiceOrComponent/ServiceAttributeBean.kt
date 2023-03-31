import com.intellij.util.xmlb.annotations.Attribute

class ServiceAttributeBean {
  @Attribute("id")
  val id: String = ""

  @Attribute("service")
  val service: String = ""
}