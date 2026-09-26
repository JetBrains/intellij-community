import org.jetbrains.annotations.Nls;

class SentenceCapitalization {

  @Nls(capitalization = Nls.Capitalization.Title)
  public String getName1() {
    return <warning descr="String '<b>hello</b>, <i>world!</i>' is not properly capitalized. It should have title capitalization">"<b><caret>hello</b>, <i>world!</i>"</warning>;
  }
}
