import org.jetbrains.annotations.Nls;

class MultipleReturns  {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        if (true) return "Foo bar";
        else return "Foo Boo";
    }
}