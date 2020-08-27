import org.jetbrains.annotations.Nls;

class TitleCapitalization  {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        return <warning descr="String 'Use .java files' is not properly capitalized. It should have title capitalization">"Use .java <caret>files"</warning>;
    }
}
