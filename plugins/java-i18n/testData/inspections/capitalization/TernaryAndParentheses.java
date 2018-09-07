import org.jetbrains.annotations.Nls;
import java.util.Map;

class TernaryAndParentheses {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    public String getName1() {
        return (<warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">"Hello World"</warning>);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    public String getName2(boolean a, boolean b) {
        return (a ? <warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">"Hello World"</warning> :
                b ? (<warning descr="String 'Goodbye World' is not properly capitalized. It should have sentence capitalization">"Goodbye World"</warning>) :
                "");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    public String getOrDefaultTest(Map<String, String> map) {
        return map.getOrDefault("key", <warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">"Hello World"</warning>);
    }

    public void foo(@Nls(capitalization = Nls.Capitalization.Sentence) String string) {

    }

    public void testConsume(boolean b) {
        foo(b ? <warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">"Hello World"</warning> : "Hello world");
    }
}
