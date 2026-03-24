import javax.swing.*;

class SetToolTipTextWithString {
  void test(JComponent comp) {
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">comp.setToolTipText("hello")</warning>;
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">comp.setToolTipText(getString())</warning>;
    comp.setToolTipText(null);
  }

  String getString() { return ""; }
}
