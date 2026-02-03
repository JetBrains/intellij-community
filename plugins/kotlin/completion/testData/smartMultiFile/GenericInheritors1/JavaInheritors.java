import p.*

class JavaInheritor1 implements KotlinInterface<I1, I2>{}

// is not suitable because type arguments do not match
class JavaInheritor2 implements KotlinInterface<I1, I3>{}
