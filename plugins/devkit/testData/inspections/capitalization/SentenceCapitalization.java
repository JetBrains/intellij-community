import org.jetbrains.annotations.Nls;

class SentenceCapitalization {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    public String getName1() {
        return <warning descr="String 'Foo Bar' is not properly capitalized. It should have sentence capitalization">"Foo B<caret>ar"</warning>;
    }
}
