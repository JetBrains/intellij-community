package typeAnnotations;

public class ArrayNestedTypeAnnotations {
    @A Z.Y.X.W[] w1;
    Z.@B Y.X.W[] @E [] w2;
    Z.Y.@C X.W @F [] @A [] @B [] w3;
    Z.Y.X.@D W @D [] w4;
    @A Z.@B Y.@C X.@D W[][] w5;
    @L Z. Y.X.@L W[] @L [] w6;
}
