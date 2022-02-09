package typeAnnotations;

public class NestedTypeAnnotations {
    @A Z.Y.X.W w1;
    Z.@B Y.X.W w2;
    Z.Y.@C X.W w3;
    Z.Y.X.@D W w4;
    @A Z.@B Y.@C X.@D W w5;
    @L Z.Y.X.W w6;
}
