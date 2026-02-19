// "Change 1st parameter of constructor 'KotlinBase' from 'String' to 'boolean'" "false"
// ACTION: Create method 'foo' in 'JavaInheritor'
// ACTION: Rename reference

public class JavaInheritor extends KotlinBase {
    public JavaInheritor(String s) {
        super(s);
    }

    void usage() {
        <caret>foo(true);
    }
}