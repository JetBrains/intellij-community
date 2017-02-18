class T {
    void f(String[] a) {
        if (a.length == 0)
            System.out.println("no"); <caret>
        else
            System.out.println(a.length);
    }
}