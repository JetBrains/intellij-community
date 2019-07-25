package xxx;

public class PackagePrivateInSignatures {
    public static PackagePrivateInterface staticField;

    public PackagePrivateClass instanceField;

    public void inParam(PackagePrivateInterface param) {
    }

    public PackagePrivateInterface inReturnType() {
        return staticField;
    }
}