package com.jetbrains.json.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.json.JsonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.json.JsonElementTypes.*;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingBuilderModel implements FormattingModelBuilder {
  private static final Logger LOG = Logger.getInstance(JsonFormattingBuilderModel.class);

  private static final TokenSet VALUES = TokenSet.create(
    OBJECT, ARRAY, STRING_LITERAL, NUMBER_LITERAL, BOOLEAN_LITERAL, NULL
  );

  private static final TokenSet SIGNS = TokenSet.create(
    L_BRACKET, R_BRACKET, L_CURLY, R_CURLY, COMMA, COLON
  );

  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("PSI Tree:\n" + DebugUtil.psiToString(element, false));
    }

    JsonBlock block = new JsonBlock(null, element.getNode(), settings, null, Indent.getNoneIndent(), null);
    if (LOG.isDebugEnabled()) {
      StringBuilder builder = new StringBuilder();
      FormattingModelDumper.dumpFormattingModel(block, 2, builder);
      LOG.debug("Format Model:\n" + builder.toString());
    }
    return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), block, settings);
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  // create spacing model once for all subsequent blocks
  static SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
    JsonCodeStyleSettings jsonSettings = settings.getCustomSettings(JsonCodeStyleSettings.class);
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JsonLanguage.INSTANCE);


    int spacesBeforeComma = commonSettings.SPACE_BEFORE_COMMA ? 1 : 0;
    int spacesBeforeColon = jsonSettings.SPACE_BEFORE_COLON ? 1 : 0;
    // not allow to keep line breaks before colon/comma, because it looks horrible
    SpacingBuilder builder = new SpacingBuilder(settings, JsonLanguage.INSTANCE)
      .before(COLON).spacing(spacesBeforeColon, spacesBeforeColon, 0, false, 0)
      .after(COLON).spaceIf(jsonSettings.SPACE_AFTER_COLON)
      .withinPair(L_BRACKET, R_BRACKET).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)
      .withinPair(L_CURLY, R_CURLY).spaceIf(jsonSettings.SPACE_WITHIN_BRACES)
      .before(COMMA).spacing(spacesBeforeComma, spacesBeforeComma, 0, false, 0)
      .after(COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA);

    return builder;
  }
}
