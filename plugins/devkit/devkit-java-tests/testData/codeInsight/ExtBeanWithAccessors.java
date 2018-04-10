import java.lang.String;

public class ExtBeanWithAccessors {
  private String field;

  @com.intellij.util.xmlb.annotations.Attribute("param")
  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }
}