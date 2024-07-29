import p.Foo;

public class Usage {
    void usage() {
        new Foo(<caret>"");
    }
}
/*
Text: (@MyAnnotation @NotNull String bar), Disabled: true, Strikeout: false, Green: false
*/
