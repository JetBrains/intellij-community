class C {
    void m() throws Exception {
        AutoCloseable r1 = null;
        try {
            System.out.println(r1 + ", " + r1);
        }
    }
}