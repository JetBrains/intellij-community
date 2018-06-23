import org.jetbrains.annotations.Nls;

class Argument  {
    public void setName(@Nls(capitalization = Nls.Capitalization.Title) String foo) {
        setName(<warning descr="String 'Foo bar' is not properly capitalized. It should have title capitalization">"Foo bar"</warning>);
    }
}