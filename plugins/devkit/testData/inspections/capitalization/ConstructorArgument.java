import org.jetbrains.annotations.Nls;

class ConstructorArgument  {
    ConstructorArgument(@Nls(capitalization = Nls.Capitalization.Title) String foo) {
    }

    public static void create() {
        new ConstructorArgument(<warning descr="String 'Foo bar' is not properly capitalized. It should have title capitalization">"Foo bar"</warning>);
    }
}