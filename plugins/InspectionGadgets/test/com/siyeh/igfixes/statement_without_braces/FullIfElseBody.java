class T {
    void f(String[] a) {
        if (a.length == 0)
            System.out.println("no");
        else
            System<caret>.out.println(a.length);
    }
}