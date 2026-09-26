import org.jetbrains.annotations.Nls;

abstract class Test {
    abstract void consumeTitle(@Nls(capitalization = Nls.Capitalization.Title) String title);
    
    abstract void consumeSentence(@Nls(capitalization = Nls.Capitalization.Sentence) String title);
    
    void test() {
        String title = <warning descr="The string is used in both title and sentence capitalization contexts">"Hello World!"</warning>;
        consumeTitle(title);
        consumeSentence(title);
        String title2 = "Hello!";
        consumeTitle(title2);
        consumeSentence(title2);
    }
}