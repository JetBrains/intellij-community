public class MyLanguageAttributeEPBean {

  @com.intellij.util.xmlb.annotations.Attribute("language")
  public String language;

  @com.intellij.util.xmlb.annotations.Attribute("hostLanguage")
  public String hostLanguage;

  @com.intellij.util.xmlb.annotations.Tag
  public String tagLanguage;
}