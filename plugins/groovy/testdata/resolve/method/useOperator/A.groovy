class Category {
    public static void r(Object o) {}
}

use(Category) {
    Object o = new Object()

    o.<caret>r()
}