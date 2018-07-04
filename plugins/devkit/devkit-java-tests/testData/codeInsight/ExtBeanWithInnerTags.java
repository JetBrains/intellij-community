import com.intellij.util.xmlb.annotations.*;

public class ExtBeanWithInnerTags {
  @Property(surroundWithTag = false)
  @XCollection
  public InnerBean[] children;
}

@Tag("inner")
class InnerBean {
  @Attribute("value")
  public String value;
}