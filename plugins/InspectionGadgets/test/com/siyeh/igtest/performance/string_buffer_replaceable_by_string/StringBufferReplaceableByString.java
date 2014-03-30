package com.siyeh.igtest.performance.string_buffer_replaceable_by_string;

public class StringBufferReplaceableByString {

  StringBuilder foo() {
    StringBuilder builder = new StringBuilder("asdfasdfasdf");
    StringBuffer buffer = new StringBuffer("test");
    StringBuilder result = new StringBuilder("return");
    return result;
  }

  public void foo1()
  {
    final StringBuffer buffer = new StringBuffer().append('a');
    System.out.println(buffer.toString());
  }

  public void foo2()
  {
    final StringBuffer buffer = new StringBuffer("foo").append("bar");
    System.out.println(buffer.toString());
  }

  public void bar(int i) {
    System.out.println(new StringBuilder("asdf").append(i).toString());
  }

  public void exceptions(String pcdata, int i) {
    StringBuilder b = new StringBuilder();
    String s = new StringBuilder().append(pcdata, 0, i).toString();
  }

  public void multiStatement() {
    StringBuilder b = new StringBuilder();
    b.append("eh");
    b.append(" yeah").append("thus...");
    System.out.println(b.toString());
  }

  public void assignment(int p) {
    StringBuilder b = new StringBuilder();
    p++;
    b.append(p);
    System.out.println(b.toString());
    StringBuilder c = new StringBuilder();
    p = 2;
    c.append(p);
    System.out.println(c.toString());

    StringBuilder d = new StringBuilder();
    d.append(p);
    System.out.println(d.toString());
  }

  void clean() {
    StringBuilder a = new StringBuilder(); // 'StringBuilder a' can be replaced with String
    StringBuilder b = new StringBuilder(); // 'StringBuilder b' can be replaced with String

    (Math.random() < 0.5 ? a : b).append("BLA");
    System.out.println(a + "/" + b);
  }

  String incomplete(char[] cs) {
    StringBuilder a = new StringBuilder();
    a.append(cs, 1);
    System.out.println(a.toString());
    StringBuilder b = new StringBuilder();
    b.append()
    return b.toString();
  }

  public static String formatOffset(long offsetMs) {
    final long minutes = offsetMs / 1000 / 60;
    final long hours = minutes / 60;
    final long min = Math.abs(minutes - hours * 60);
    StringBuilder data = new StringBuilder();
    if (hours > 0) data.append('+');
    if (hours < 0) data.append('-');
    data.append(String.format("%02d:%02d", Math.abs(hours), min));
    return data.toString();
  }

  class HighlightStaticImport {

    void example1() {
      System.out.println();
      final StringBuilder builder = new StringBuilder();
      builder.append(foo1());
      b(); // side effect
      builder.append(foo1());
      bar(builder.toString());
      System.out.println();
    }

    void example2() {
      final StringBuilder builder = new StringBuilder();
      builder.append(foo1());
      b(); // side effect, but has no effect on builder anymore
      bar(builder.toString());
    }

    String s;

    void b() {
      s = "asdf";
    }

    private void bar(String s) {

    }

    private String foo1() {
      return null;
    }
  }

  void a(Repository repository, Integer workingCopyId) {
    final StringBuilder builder = new StringBuilder();
    builder.append(executeAndDumpGetRequest("/repositories/" + toPathComponent(repository.name()) + "/workingCopies/" + toPathComponent(workingCopyId.toString()) + "/changes"));
    builder.append('\n');
    builder.append(executeAndDumpGetRequest("/repositories/" + toPathComponent(repository.name()) + "/workingCopies/" + toPathComponent(workingCopyId.toString()) + "/changes/_"));
    builder.append('\n');
    builder.append(executeAndDumpGetRequest("/repositories/" + toPathComponent(repository.name()) + "/workingCopies/" + toPathComponent(workingCopyId.toString()) + "/changes/__"));
    checkOutputFiles(new TestDataFile[]{new TestDataFile(testName(), builder.toString())});
  }
  private void checkOutputFiles(TestDataFile[] testDataFiles) {}
  private String executeAndDumpGetRequest(String s) { return s; }
  private String toPathComponent(String name) { return name; }
  class Repository { public String name() { return "name"; } }
  public String testName() { return "test"; }
  class TestDataFile { public TestDataFile(String s, String s1) { } }

  String methodCallArgumentToConstructor(String a, String b, String c) {
    final StringBuilder builder = new StringBuilder(a.substring(1));
    builder.append(b).append(' ');
    builder.append(c);
    return builder.toString();
  }
}
