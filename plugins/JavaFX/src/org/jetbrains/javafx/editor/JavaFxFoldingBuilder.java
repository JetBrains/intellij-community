package org.jetbrains.javafx.editor;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxFile;
import org.jetbrains.javafx.lang.psi.JavaFxImportList;
import org.jetbrains.javafx.lang.psi.JavaFxImportStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Code folding support
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxFoldingBuilder implements FoldingBuilder, DumbAware {
  private static final TokenSet DEFINITIONS = TokenSet.create(
    JavaFxElementTypes.OBJECT_LITERAL,
    JavaFxElementTypes.FUNCTION_DEFINITION,
    JavaFxElementTypes.VARIABLE_DECLARATION,
    JavaFxElementTypes.CLASS_DEFINITION);

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull final ASTNode node, @NotNull final Document document) {
    final List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node.getPsi(), document, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private static void appendDescriptorsForImports(final JavaFxFile fxFile, final List<FoldingDescriptor> descriptors) {
    final JavaFxImportList[] importLists = fxFile.getImportLists();
    for (JavaFxImportList importList : importLists) {
      final JavaFxImportStatement[] importStatements = importList.getImportStatements();
      if (importStatements.length > 1) {
        final int tail = "import ".length();
        descriptors.add(new FoldingDescriptor(importList.getNode(), new TextRange(importList.getTextOffset() + tail,
                                                                                            importList.getTextRange()
                                                                                              .getEndOffset())));
      }
    }
  }

  private static void appendDescriptors(PsiElement element, Document document, List<FoldingDescriptor> descriptors) {
    final ASTNode node = element.getNode();
    if (node == null) {
      return;
    }
    final IElementType type = node.getElementType();

    // comments
    if ((type == JavaFxTokenTypes.C_STYLE_COMMENT || type == JavaFxTokenTypes.DOC_COMMENT) &&
        isMultiline(element, document) &&
        isWellEndedComment(element)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
    else if (DEFINITIONS.contains(type) && isMultiline(element, document)) {
      int leftBracePosition = element.getText().indexOf('{');
      if (leftBracePosition != -1) {
        descriptors.add(new FoldingDescriptor(node,
                                              new TextRange(node.getStartOffset() + leftBracePosition,
                                                            node.getTextRange().getEndOffset())));
      }
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      appendDescriptors(child, document, descriptors);
      child = child.getNextSibling();
    }

    if (element instanceof JavaFxFile) {
      appendDescriptorsForImports((JavaFxFile)element, descriptors);
    }
  }

  private static boolean isWellEndedComment(final PsiElement element) {
    return element.getText().endsWith("*/");
  }

  private static boolean isMultiline(PsiElement element, Document document) {
    final int start = document.getLineNumber(element.getTextOffset());
    final int end = document.getLineNumber(element.getTextRange().getEndOffset());
    return start != end;
  }

  @Nullable
  public String getPlaceholderText(@NotNull final ASTNode node) {
    final IElementType elementType = node.getElementType();
    if (elementType == JavaFxTokenTypes.C_STYLE_COMMENT) {
      return "/*...*/";
    }
    if (elementType == JavaFxTokenTypes.DOC_COMMENT) {
      return "/**...*/";
    }
    if (elementType == JavaFxElementTypes.IMPORT_LIST) {
      return "...";
    }
    if (DEFINITIONS.contains(elementType)) {
      return "{...}";
    }
    return null;
  }

  public boolean isCollapsedByDefault(@NotNull final ASTNode node) {
    if (node.getElementType() == JavaFxElementTypes.IMPORT_LIST) {
      return JavaCodeFoldingSettings.getInstance().isCollapseImports();
    }
    if (node.getElementType() == JavaFxTokenTypes.C_STYLE_COMMENT) {
      return JavaCodeFoldingSettings.getInstance().isCollapseFileHeader();
    }
    if (node.getElementType() == JavaFxTokenTypes.DOC_COMMENT) {
      return JavaCodeFoldingSettings.getInstance().isCollapseJavadocs();
    }
    if (node.getElementType() == JavaFxElementTypes.FUNCTION_DEFINITION) {
      return JavaCodeFoldingSettings.getInstance().isCollapseMethods();
    }
    return false;
  }
}
