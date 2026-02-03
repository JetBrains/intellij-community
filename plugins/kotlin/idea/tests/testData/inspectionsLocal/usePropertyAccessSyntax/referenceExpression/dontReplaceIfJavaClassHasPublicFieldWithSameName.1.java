public class TestClass {
    // notice these properties are public
    public int value;
    public boolean isSetValue;
    private int myVal;

    public void setValue(int value) {
        this.value = value;
        this.isSetValue = true;
    }
    public int getValue() {
        return this.value;
    }
    public boolean getIsSetValue() {
        return this.isSetValue;
    }
}