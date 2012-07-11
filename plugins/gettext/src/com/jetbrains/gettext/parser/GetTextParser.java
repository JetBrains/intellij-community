package com.jetbrains.gettext.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextCompositeElementTypes;
import com.jetbrains.gettext.GetTextTokenTypes;
import com.jetbrains.gettext.lang.MsgCommandContainer;
import com.jetbrains.gettext.lang.UnknownCommandException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextParser implements PsiParser {
  @NotNull
  @Override
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    builder.setDebugMode(true);
    final PsiBuilder.Marker marker = builder.mark();
    while (!builder.eof()) {
      parseMsgBock(builder);
    }
    marker.done(root);
    return builder.getTreeBuilt();
  }

  private static void parseMsgBock(PsiBuilder builder) {
    PsiBuilder.Marker msgidMarker = builder.mark();
    parseCommentHeader(builder);
    parseMsgContent(builder);
    msgidMarker.done(GetTextCompositeElementTypes.MSG_BLOCK);
  }

  private static void parseCommentHeader(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    while (GetTextTokenTypes.COMMENTS.contains(builder.getTokenType()) ||
           GetTextTokenTypes.FLAG_LINE.contains(builder.getTokenType())) {
      builder.advanceLexer();
    }
    marker.done(GetTextCompositeElementTypes.HEADER);
  }

  private static boolean parseMsgContent(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    MsgCommandContainer container = new MsgCommandContainer();
    while (!canTerminate(builder, container)) {
      try {
        IElementType token = builder.getTokenType();
        if (container.parse(builder) && !container.addCommand(token)) {
          break;
        }
      }
      catch (UnknownCommandException e) {
        //marker.precede().error(e.getMessage());
        //builder.advanceLexer();
      }
    }
    if (!container.isFull()) {
      marker.error("Not enough commands");
      return false;
    }
    else {
      marker.done(GetTextCompositeElementTypes.MSG_CONTENT);
      return true;
    }
  }

  private static boolean canTerminate(PsiBuilder builder, MsgCommandContainer container) {
    return builder.eof() ||
           GetTextTokenTypes.SYSTEM_COMMENTS.contains(builder.getTokenType()) ||
           builder.getTokenType() == GetTextTokenTypes.MSGID && container.isFull();
  }
}
