class UnnecessaryCall {
    void sb(StringBuilder sb, int a, int b) {
        sb.append(<caret>Integer.toString(a + b));
        sb.append("Number: " + Integer.toString(a + b));
        sb.append(Integer.toString(++a));
        sb.append("Number: " + Integer.toString(a << b));
        sb.append(Integer.toString(a - b) + "Number");
        sb.append(Integer.toString(a << b) + "Number");
    }
}