import org.jetbrains.annotations.Nls;

class Test {
    native void consumeSentence(@Nls(capitalization = Nls.Capitalization.Sentence) String sentence);
 
    void test() {
        String s = String.format("Hello %s", "world");
        consumeSentence(s);
        String s1 = "world";
        consumeSentence("Hello "+s1);
    }
}