package typeAnnotations;

public class GenericNestedTypeAnnotations {
    @A V.U<String>.T<Boolean, Integer, Float> t1;
    V.@B U<String>.T<Boolean, Integer, Float> t2;
    V.U<String>.@C T<Boolean, Integer, Float> t3;
    V.U<@D String>.T<Boolean, Integer, Float> t4;
    V.U<String>.T<@E Boolean, @F Integer, Float> t5;
    @L V.U<String>.T<@L Boolean, @F Integer, Float> t6;
}
