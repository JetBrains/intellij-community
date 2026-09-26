// KT-4355 IDEA complains when assigning Kotlin objects where java.lang.Object is expected
class AssignKotlinClassToObjectInJava {
    void test(KotlinInterface i) {
        Object kotlinClass = new KotlinClass();
        Object kotlinInterface = i;

        KotlinClass foo = null;
        foo.equals(foo);
    }
}
