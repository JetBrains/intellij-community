package pkg;

public class NestedTypeAnnotationsParameters {
    public static void doSomething(
            @A Z.Y.X.W w1,
            Z.@B Y.X.W w2,
            Z.Y.@C X.W w3,
            Z.Y.X.@D W w4,
            @A Z.@B Y.@C X.@D W w5,
            @L Z.Y.X.W w6,
            P.@A Q.R r1,
            P.Q.@B R r2,
            S.T.@C U u1,
            T.@A Y.U.I.O o1
    ) {

    }

    public static void main(String[] args) {

    }
}
