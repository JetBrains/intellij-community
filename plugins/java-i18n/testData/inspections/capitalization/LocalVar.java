import org.jetbrains.annotations.Nls;

class Test {
    native void consumeTitle(@Nls(capitalization = Nls.Capitalization.Title) String title);
 
    void test() {
        String s = <warning descr="String 'improperly capitalized text' is not properly capitalized. It should have title capitalization">"improperly capitalized text"</warning>;
        consumeTitle(s);
    }
}