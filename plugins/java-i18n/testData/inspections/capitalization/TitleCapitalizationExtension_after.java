import org.jetbrains.annotations.Nls;

class TitleCapitalization  {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        return "Use .java *.java _Java ~java <caret>Files";
    }
}
