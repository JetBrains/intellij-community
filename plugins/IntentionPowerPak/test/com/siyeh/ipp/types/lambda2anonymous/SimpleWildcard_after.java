class Test1<U> {

    public static void main(String[] args) {
        Comparable<? extends Integer> c = new Comparable<Integer>() {
            @Override
            public int compareTo(Integer o) {
                <selection>return 0;</selection>
            }
        };
    }
}