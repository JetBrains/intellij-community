import javax.swing.*;

class SetToolTipTextWithStringFixRaw {
  void test(JComponent comp) {
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">comp.setToolTipText(<caret>"hello")</warning>;
  }
}
