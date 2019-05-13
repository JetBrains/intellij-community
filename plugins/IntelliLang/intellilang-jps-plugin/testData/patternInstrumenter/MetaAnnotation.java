import org.intellij.lang.annotations.Pattern;

public class MetaAnnotation {
  @Pattern("\\d+")
  private @interface Meta { }

  @Meta
  public String metaAnnotation() {
    return "-";
  }
}