import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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

  @Attribute
  @NonNls
  public String noSpellCheck;

  @Tag
  @Nls(capitalization=Nls.Capitalization.Title)
  public String tagTitle;

  @Tag
  @NonNls
  public String tagNoSpellCheck;
}