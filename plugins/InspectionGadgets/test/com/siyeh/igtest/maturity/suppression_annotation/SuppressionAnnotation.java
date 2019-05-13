<warning descr="Inspection suppression annotation '@SuppressWarnings({\\"ALL\\", \\"SuppressionAnnotation\\"})'">@SuppressWarnings({"ALL", "SuppressionAnnotation"})</warning>
public class SuppressionAnnotation {

  <warning descr="Inspection suppression annotation '@SuppressWarnings(\\"PublicField\\")'">@SuppressWarnings("PublicField")</warning>
  public String s;

  <warning descr="Inspection suppression annotation '@SuppressWarnings({})'">@SuppressWarnings({})</warning>
  public String t;

  void foo() {
    <warning descr="Inspection suppression annotation '//noinspection HardCodedStringLiteral'">//noinspection HardCodedStringLiteral</warning>
    System.out.println("hello");
    <warning descr="Inspection suppression annotation '//noinspection'">//noinspection</warning>
    System.out.println();
  }

  @SuppressWarnings("FreeSpeech")
  void bar() {
    //noinspection FreeSpeech
    System.out.println();
  }
}