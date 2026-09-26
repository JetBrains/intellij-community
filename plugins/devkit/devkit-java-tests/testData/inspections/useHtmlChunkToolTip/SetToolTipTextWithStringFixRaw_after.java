import com.intellij.ide.HelpTooltipKt;
import com.intellij.openapi.util.text.HtmlChunk;

import javax.swing.*;

class SetToolTipTextWithStringFixRaw {
  void test(JComponent comp) {
    HelpTooltipKt.setToolTipText(comp, HtmlChunk.raw("hello"));
  }
}
