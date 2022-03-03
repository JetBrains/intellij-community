package java11;

public class TestJava11StringConcatEmptyAffix {

    public String testEmptyPrefixInt(int value) {
        return "" + value;
    }

    public String testEmptyPrefixString(String value) {
        return "" + value;
    }

    public String testPrefixInt(int value) {
        return "prefix" + value;
    }

    public String testPrefixString(String value) {
        return "prefix" + value;
    }

    // NOTE: Empty suffix is indistinguishable from empty prefix in bytecode. Will be decompiled as latter.
    public String testIntEmptySuffix(int value) {
        return value + "";
    }

    // NOTE: Empty suffix is indistinguishable from empty prefix in bytecode. Will be decompiled as latter.
    public String testStringEmptySuffix(String value) {
        return value + "";
    }

    public String testIntSuffix(int value) {
        return value + "suffix";
    }

    public String testStringSuffix(String value) {
        return value + "suffix";
    }

    public String testIntInt(int first, int second) {
        return "" + first + second;
    }

    public String testIntIntSuffix(int first, int second) {
        return "" + first + second + "suffix";
    }

    public String testIntString(int intValue, String stringValue) {
        return "" + intValue + stringValue;
    }

    public String testStringInt(int intValue, String stringValue) {
        return stringValue + intValue;
    }

}