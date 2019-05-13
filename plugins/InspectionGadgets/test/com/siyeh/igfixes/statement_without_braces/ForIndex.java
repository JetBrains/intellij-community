class T {
    void f(String[] a) {
        for<caret> (int i = 0; i < a.length; i++)
            System.out.println(a[i]);
    }
}