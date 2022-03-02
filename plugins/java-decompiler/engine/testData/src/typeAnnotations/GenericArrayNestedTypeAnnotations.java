package typeAnnotations;

public class GenericArrayNestedTypeAnnotations {
    @A V.U<String>.T<Boolean, Integer, Float> @A [] t1;
    V.@B U<String>.T<Boolean, Integer, Float>[] t2;
    V.U<String>.@C T<Boolean, Integer, Float> @B [] @D [] t3;
    V.U<@D String>.T<Boolean, Integer, Float> @F [] t4;
    @B V.@A U<@A String>.@A T<@E Boolean, @F Integer, Float>[] t5;
    @L V.U<String>.T<Boolean, Integer, Float>[][] t6;
}
