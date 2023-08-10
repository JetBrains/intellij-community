import testData.libraries.*;
import java.util.*;

class TestOverload {
    {
        System.out.println(ExtensionPropertyKt.getExProp(""));
        ArrayList<String> p = new ArrayList<>();
        System.out.println(ExtensionPropertyKt.getExProp(p));
        System.out.println(ExtensionPropertyKt.getExPropWithContextReceiver("context", p));
    }
}