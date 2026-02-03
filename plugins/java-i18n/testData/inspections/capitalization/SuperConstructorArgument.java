import org.jetbrains.annotations.Nls;

class SuperConstructorArgument  {
    SuperConstructorArgument(@Nls(capitalization = Nls.Capitalization.Title) String foo) {
    }

    public static class SubClass extends SuperConstructorArgument {
        public SubClass() {
            super(<warning descr="String 'Foo bar' is not properly capitalized. It should have title capitalization">"Foo bar"</warning>);
        }
    }

}