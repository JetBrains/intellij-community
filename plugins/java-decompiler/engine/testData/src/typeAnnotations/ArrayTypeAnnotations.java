package typeAnnotations;

public class ArrayTypeAnnotations implements ParentInterface {
    @A String[] s1 = new String[0];
    String @B [] s2 = new String[0];
    String @C [][] s3 = new String[0][0];
    String [] @D [] s4 = new String[0][0];
    @A String[] s5() { return null; }
    String @B [] s6() { return null; }
    @A String @B [] @C [] @D [] s7() { return null; }
    @L String @L [][] @L [] s8() { return null; }
}
