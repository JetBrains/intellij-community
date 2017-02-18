class LongDemo {
    private long <caret>n;

    public long getN() {
        return n;
    }

    public void setN(long n) {
        this.n = n;
    }

    public void preInc() {
        ++n;
    }

    public long preIncVal() {
        return ++n;
    }

    public void postDec() {
        n--;
    }

    public void twice() {
        n *= 2;
    }

    public void half() {
        n >>= 1;
    }

    public long plusOne() {
        return n + 1;
    }

    public L lambda() {
        return () -> n;
    }

    public String toString() {
        return "n=" + n;
    }

    interface L {
        long get();
    }
}