class X {
    private static int x = 0

    public static int getX() {
        return x;
    }

    public static void setX(int x) {
        X.x = x;
    }
}

class Y extends X {


}
