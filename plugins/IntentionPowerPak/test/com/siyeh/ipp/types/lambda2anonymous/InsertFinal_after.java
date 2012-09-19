class Test1 {
    void foo(){}
    {
        Comparable<String> a = new Comparable<String>() {
            @Override
            public int compareTo(String o) {
                <selection>new Runnable() {
                    @Override
                    public void run() {
                        System.out.println(o);
                    }
                }.run();
                return 0;</selection>
            }
        };
    }
}