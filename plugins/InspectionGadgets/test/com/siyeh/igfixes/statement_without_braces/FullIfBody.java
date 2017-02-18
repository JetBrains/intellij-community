class T {
    void f(String[] a) {
        if (a.length == 0)
            System<caret>.out.println("no");
        else
            System.out.println(a.length);
    }
}