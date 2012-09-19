class Test1 {
    void foo(){}
    {
        String str = "effectively final string";
        String str1 = "effectively final string";
        Comparable<String> a = new Comparable<String>() {
            @Override
            public int compareTo(String o) {
                <selection>System.out.println(str1);
                new Runnable() {
                    @Override
                    public void run() {
                        System.out.println(o);
                        System.out.println(str);
                    }
                }.run();
                return 0;</selection>
            }
        };
    }
}