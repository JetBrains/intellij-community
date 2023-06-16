import com.intellij.util.xmlb.annotations.Attribute

class ServiceAttributeBean {
  @Attribute("id")
  String id = ""

  @Attribute("service")
  String service = ""
}