import org.jetbrains.annotations.Nls;

class SentenceCapitalization {

  @Nls(capitalization = Nls.Capitalization.Title)
  public String getName1() {
    return "<b>Hello</b>, <i>World!</i>";
  }
}
