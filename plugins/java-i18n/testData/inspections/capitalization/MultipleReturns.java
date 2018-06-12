import org.jetbrains.annotations.Nls;

class MultipleReturns  {
    @Nls(capitalization = Nls.Capitalization.Title)
    public String getName() {
        if (true) return <warning descr="String 'Foo bar' is not properly capitalized. It should have title capitalization">"Foo bar"</warning>;
        else return <warning descr="String 'Foo boo' is not properly capitalized. It should have title capitalization">"Foo <caret>boo"</warning>;
    }
}