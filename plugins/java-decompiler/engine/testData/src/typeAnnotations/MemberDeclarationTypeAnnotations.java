package typeAnnotations;

import java.io.IOException;
import java.io.Serializable;

public class MemberDeclarationTypeAnnotations<@A P extends @B Number & @F Serializable> {
    @L String s1 = "";

    @B int f1 = 0;

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
}