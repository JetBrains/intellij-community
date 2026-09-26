import com.intellij.ide.HelpTooltipKt;
import com.intellij.openapi.util.text.HtmlChunk;

import javax.swing.*;

class SetToolTipTextWithStringFix {
  void test(JComponent comp) {
    HelpTooltipKt.setToolTipText(comp, HtmlChunk.text("hello"));
  }
}
