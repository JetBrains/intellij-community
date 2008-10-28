class A {
    static def fact = {
        int i ->
        if (i > 1) {
            return A.fact(i - 1)
        } else {
            return 1
        }
    }

    public static void main(String[] args) {
        Closure cl = A.fact
        int var = cl(5)
        println(<ref>var.intValue())
    }
}