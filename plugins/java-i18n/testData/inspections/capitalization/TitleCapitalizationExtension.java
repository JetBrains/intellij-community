import org.jetbrains.annotations.Nls;

class TitleCapitalization  {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        return <warning descr="String 'Use .java *.java _java ~java files' is not properly capitalized. It should have title capitalization">"Use .java *.java _java ~java <caret>files"</warning>;
    }
}
