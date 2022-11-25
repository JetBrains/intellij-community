package sample;

import static sample.KotlinEnum0.values;

enum JavaEnum {}

class Test {
    public static void mustReport() {
        KotlinEnum0[] valuesKt = KotlinEnum0.values();
        KotlinEnum0[] staticImport = values();
        KotlinEnum3[] accessThroughMember = KotlinEnum3.ONE.values();
        KotlinEnum4[] withAbstractFun = KotlinEnum4.ONE.values();
    }

    public static void mustNotReport() {
        JavaEnum[] javaEnumValues = JavaEnum.values();
        KotlinEnum0[] anotherMethodCalledValues = KotlinEnum0.values(true);
        int anotherValuesWithoutArgs = KotlinEnum2.Companion.values();
    }

    public static void doesntCompileSoItDoesntMatterWhetherToReportOrNot() {
        KotlinEnum2.values();
        KotlinEnum5.values();
        KotlinEnum6.values();
    }
}