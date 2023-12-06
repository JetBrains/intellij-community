import p2.KotlinInterface

public abstract class JavaInheritor1 implements KotlinInterface {
}

public class JavaInheritor2 extends JavaInheritor1 {
    public JavaInheritor2() {
    }

    public JavaInheritor2(int p) {
    }
}

// not visible - it's package local
class JavaInheritor3 extends KotlinInterface {}

