// "Remove 2nd parameter from constructor 'KotlinBase'" "false"
// ACTION: Create method 'foo' in 'JavaInheritor'
// ACTION: Rename reference

public class JavaInheritor extends KotlinBase {
    public JavaInheritor() {
        super("", 1);
    }

    void usage() {
        <caret>foo("");
    }
}