package typeAnnotations;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Consumer;

public class MemberDeclarationTypeAnnotations<@A P extends @B Number & @F Serializable> {
    @L String s1 = "";

    @B int f1 = 0;

    Consumer<String> c = (@A String s) -> System.out.println(s);

    SomeFunInterface<String, String> sf = (String s1, @B String s2) -> System.out.println(s1);

    @K
    public @L @A MemberDeclarationTypeAnnotations() {
    }

    @K
    public <@A T extends @B Number & @F Serializable> @L @C Number foo(@D T @E [] a) {
        return 0;
    }

    @K
    public <T> @C Number bar(@D T @E [] a) throws @A IOException, @B IllegalStateException {
        return 0;
    }

    public void fooBar(@L @A String param1, @L @K @B String param2) { }
}