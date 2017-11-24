class Literal {

    void f(String text, String replacement) {
        text.replace<caret>("abc", replacement);//end line comment
    }
}