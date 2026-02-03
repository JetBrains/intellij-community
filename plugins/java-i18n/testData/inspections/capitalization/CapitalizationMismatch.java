import org.jetbrains.annotations.Nls;

abstract class Test {
    abstract @Nls(capitalization = Nls.Capitalization.Sentence) String getSentence();
    
    abstract void consumeTitle(@Nls(capitalization = Nls.Capitalization.Title) String title);
    
    void test() {
        consumeTitle(<warning descr="The sentence capitalization is provided where title capitalization is required">getSentence()</warning>);
    }
}