import org.jetbrains.annotations.Nls;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

public class MyExtensionPoint {

  @Attribute
  @Nls(capitalization=Nls.Capitalization.Title)
  public String titleProperty;

  @Attribute
  @Nls(capitalization=Nls.Capitalization.Sentence)
  public String sentenceProperty;

  @Attribute
  @Nls
  public String unspecifiedProperty;

  @Tag
  @Nls(capitalization=Nls.Capitalization.Title)
  public String tagTitle;
}