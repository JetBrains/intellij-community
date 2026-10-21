public class A {
    private String text;

    public String getText() {
        return text;
    }

    public void setText(CharSequence text) {
        this.text = text.toString();
    }
}