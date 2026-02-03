import org.jetbrains.annotations.Nls;

class Mnemonics {

  @Nls(capitalization = Nls.Capitalization.Title)
  public String getName1() {
    return <warning descr="String 'Compare with clip_board' is not properly capitalized. It should have title capitalization">"Compare <caret>with clip_board"</warning>;
  }
}
