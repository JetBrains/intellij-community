package org.jetbrains.android.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.formatter.xml.XmlTagBlock;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidXmlFormattingModel implements FormattingModel {
  private final FormattingModel myModel;
  private final Block myRootBlock;

  public AndroidXmlFormattingModel(FormattingModel model,
                                   CodeStyleSettings settings,
                                   final AndroidXmlCodeStyleSettings.MySettings customSettings) {
    myModel = model;
    final Block block = myModel.getRootBlock();

    if (block instanceof XmlBlock) {
      final XmlBlock b = (XmlBlock)block;
      final XmlPolicy policy = customSettings.createXmlPolicy(settings, getDocumentModel());
      myRootBlock = new XmlBlock(b.getNode(), b.getWrap(), b.getAlignment(), policy, b.getIndent(), b.getTextRange()) {
        @Override
        protected XmlTagBlock createTagBlock(ASTNode child, Indent indent, Wrap wrap, Alignment alignment) {
          return new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy,
                                 indent != null ? indent : Indent.getNoneIndent(),
                                 isPreserveSpace());
        }
      };
    }
    else {
      myRootBlock = block;
    }
  }

  @NotNull
  public Block getRootBlock() {
    return myRootBlock;
  }

  @NotNull
  public FormattingDocumentModel getDocumentModel() {
    return myModel.getDocumentModel();
  }

  public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    return myModel.replaceWhiteSpace(textRange, whiteSpace);
  }

  public TextRange shiftIndentInsideRange(TextRange range, int indent) {
    return myModel.shiftIndentInsideRange(range, indent);
  }

  public void commitChanges() {
    myModel.commitChanges();
  }
}
