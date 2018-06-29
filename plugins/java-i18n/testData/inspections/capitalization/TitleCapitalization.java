import org.jetbrains.annotations.Nls;

class TitleCapitalization  {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        return <warning descr="String 'Foo bar' is not properly capitalized. It should have title capitalization">"Foo b<caret>ar"</warning>;
    }
}
