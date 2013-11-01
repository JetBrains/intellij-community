package com.jetbrains.json.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.json.psi.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.json.JsonParserDefinition.CONTAINERS;
import static com.jetbrains.json.JsonParserTypes.*;

/**
 * @author Mikhail Golubev
 */
public class JsonBlock implements ASTBlock {
  private static final TokenSet OPEN_BRACES = TokenSet.create(L_BRAKET, L_CURLY);
  private static final TokenSet CLOSE_BRACES = TokenSet.create(R_BRAKET, R_CURLY);
  private static final TokenSet BRACES = TokenSet.orSet(OPEN_BRACES, CLOSE_BRACES);
  private JsonBlock myParent;


  private ASTNode myNode;
  private IElementType myNodeType;
  private Alignment myAlignment;
  private Indent myIndent;
  private Wrap myWrap;
  private CodeStyleSettings mySettings;
  private SpacingBuilder mySpacingBuilder;
  // lazy initialized on first call to #getSubBlocks()
  private List<Block> mySubBlocks = null;

  private Wrap myChildWrap = null;
  private Alignment myChildAlignment = null;

  public JsonBlock(JsonBlock parent, ASTNode node, CodeStyleSettings settings, Alignment alignment, Indent indent, Wrap wrap) {
    myParent = parent;
    myNode = node;
    myNodeType = node.getElementType();
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
    mySettings = settings;

    mySpacingBuilder = JsonFormattingBuilderModel.createSpacingBuilder(settings);

    if (CONTAINERS.contains(myNode.getElementType())) {
      myChildWrap = Wrap.createWrap(WrapType.NORMAL, false);
      myChildAlignment = Alignment.createAlignment();
    }
  }

  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = ContainerUtil.mapNotNull(myNode.getChildren(null), new Function<ASTNode, Block>() {
        @Override
        public Block fun(ASTNode node) {
          if (node.getElementType() == TokenType.WHITE_SPACE || node.getTextLength() == 0) {
            return null;
          }
          return createSubBlock(node);
        }
      });
    }
    return mySubBlocks;
  }

  private Block createSubBlock(ASTNode childNode) {
    IElementType childNodeType = childNode.getElementType();

    Indent indent = Indent.getNoneIndent();
    Alignment alignment = null;
    Wrap wrap = null;

    if (CONTAINERS.contains(myNode.getElementType())) {
      if (childNodeType != COMMA && !BRACES.contains(childNodeType)) {
        wrap = Wrap.createWrap(WrapType.NORMAL, true);
        alignment = myChildAlignment;
        indent = Indent.getNormalIndent();
      }
    }
    return new JsonBlock(this, childNode, mySettings, alignment, indent, wrap);
  }

  @Nullable
  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return mySpacingBuilder.getSpacing(this, child1, child2);
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    Indent indent = Indent.getNormalIndent();
    Alignment alignment = myChildAlignment;

    if (CONTAINERS.contains(myNodeType) && newChildIndex != 0) {
      IElementType prevChildType = ((JsonBlock)mySubBlocks.get(newChildIndex - 1)).myNode.getElementType();
      if (OPEN_BRACES.contains(prevChildType)) {
        indent = Indent.getNormalIndent();
      }
    }
    return new ChildAttributes(indent, alignment);
//    return ChildAttributes.DELEGATE_TO_PREV_CHILD;
  }

  @Override
  public boolean isIncomplete() {
    IElementType nodeType = myNode.getElementType();
    ASTNode lastChildNode = myNode.getLastChildNode();
    PsiElement psiElement = myNode.getPsi();
    if (nodeType == OBJECT) {
      return lastChildNode != null && lastChildNode.getElementType() == R_CURLY;
    }
    else if (nodeType == ARRAY) {
      return lastChildNode != null && lastChildNode.getElementType() == R_BRAKET;
    }
    else if (psiElement instanceof JsonProperty) {
      return ((JsonProperty)psiElement).getValue() != null;
    }
    return false;
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }
}
