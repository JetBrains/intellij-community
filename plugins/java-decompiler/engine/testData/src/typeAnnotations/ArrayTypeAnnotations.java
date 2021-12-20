package typeAnnotations;

public class ArrayTypeAnnotations implements ParentInterface {
    @A String[] s1 = new String[0];

    String @B [] s2 = new String[0];

    String @C [][] s3 = new String[0][0];

    String [] @D [] s4 = new String[0][0];
}
