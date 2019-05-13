class T {
    void f(String[] a) {
        int j = 0;
        do System.out.println(a[j++]);<caret>
        while (j < a.length);
    }
}