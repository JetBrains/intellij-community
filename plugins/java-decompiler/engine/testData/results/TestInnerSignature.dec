package pkg;

public class TestInnerSignature<A, B, C> {
   A a;
   B b;
   C c;

   public TestInnerSignature(A a, @Deprecated B b, C c) {
      this.a = a;// 9
      this.b = b;// 10
      this.c = c;// 11
   }// 12

   public class Inner {
      A a;
      B b;
      C c;

      public Inner(A a, @Deprecated B b, C c) {
         this.a = a;// 20
         this.b = b;// 21
         this.c = c;// 22
      }// 23
   }

   public static class InnerStatic<A, B, C> {
      A a;
      B b;
      C c;

      public InnerStatic(A a, @Deprecated B b, C c) {
         this.a = a;// 32
         this.b = b;// 33
         this.c = c;// 34
      }// 35
   }
}

class 'pkg/TestInnerSignature' {
   method '<init> (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V' {
      4      8
      5      8
      6      8
      7      8
      8      8
      9      9
      a      9
      b      9
      c      9
      d      9
      e      10
      f      10
      10      10
      11      10
      12      10
      13      11
   }
}

class 'pkg/TestInnerSignature$Inner' {
   method '<init> (Lpkg/TestInnerSignature;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V' {
      9      19
      a      19
      b      19
      c      19
      d      19
      e      20
      f      20
      10      20
      11      20
      12      20
      13      21
      14      21
      15      21
      16      21
      17      21
      18      21
      19      22
   }
}

class 'pkg/TestInnerSignature$InnerStatic' {
   method '<init> (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V' {
      4      31
      5      31
      6      31
      7      31
      8      31
      9      32
      a      32
      b      32
      c      32
      d      32
      e      33
      f      33
      10      33
      11      33
      12      33
      13      34
   }
}

Lines mapping:
9 <-> 9
10 <-> 10
11 <-> 11
12 <-> 12
20 <-> 20
21 <-> 21
22 <-> 22
23 <-> 23
32 <-> 32
33 <-> 33
34 <-> 34
35 <-> 35
Not mapped:
8
19
31
