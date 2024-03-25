public class ClassWithPublicFieldWithSameName {
    // notice these properties are public
    public int value;
    public boolean isSetValue;

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