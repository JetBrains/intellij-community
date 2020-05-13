public class ExtensionDefinesWithTagViaAnnotation {

  @com.intellij.util.xmlb.annotations.Tag("customTagName")
  private String myTag;

  private String myTagWithoutAnnotation;
}