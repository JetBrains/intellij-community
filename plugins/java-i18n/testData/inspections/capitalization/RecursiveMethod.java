import org.jetbrains.annotations.Nls;

class RecursiveMethod {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        return getName();
    }
}