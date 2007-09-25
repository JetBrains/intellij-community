package org.jetbrains.plugins.groovy.annotator.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.09.2007
 */
public class ChangeExtendsImplementsQuickFix implements IntentionAction {
  private final GrExtendsClause myExtendsClause;
  private final GrImplementsClause myImplementsClause;

  final GrTypeDefinition myClass;

  public ChangeExtendsImplementsQuickFix(@Nullable GrExtendsClause extendsClause, @Nullable GrImplementsClause implementsClause) {
    myExtendsClause = extendsClause;
    myImplementsClause = implementsClause;

    PsiElement myClassElement = null;
    if (myImplementsClause != null) {
      myClassElement = myImplementsClause.getParent();
    } else if (myExtendsClause != null) {
      myClassElement = myExtendsClause.getParent();
    }

    assert myClassElement != null;
    myClass = (GrTypeDefinition) myClassElement;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("change.implements.and.extends.classes");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myClass != null && myClass.isValid() && myClass.getManager().isInProject(file);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    GrCodeReferenceElement[] extendsReferenceElements = GrCodeReferenceElement.EMPTY_ARRAY;
    GrCodeReferenceElement[] implementsReferenceElements = GrCodeReferenceElement.EMPTY_ARRAY;
    if (myExtendsClause != null) {
      extendsReferenceElements = myExtendsClause.getReferenceElements();
    }

    if (myImplementsClause != null) {
      implementsReferenceElements = myImplementsClause.getReferenceElements();
    }

    List<GrCodeReferenceElement> classes = new ArrayList<GrCodeReferenceElement>();
    List<GrCodeReferenceElement> interfaces = new ArrayList<GrCodeReferenceElement>();

    for (GrCodeReferenceElement extendsReferenceElement : extendsReferenceElements) {
      final PsiElement extendsElement = extendsReferenceElement.resolve();
      if (extendsElement == null || !(extendsElement instanceof PsiClass)) continue;

      if (((PsiClass) extendsElement).isInterface()) {
        interfaces.add(extendsReferenceElement);
      } else {
        classes.add(extendsReferenceElement);
      }
    }

    for (GrCodeReferenceElement implementsReferenceElement : implementsReferenceElements) {
      final PsiElement implementsElement = implementsReferenceElement.resolve();
      if (implementsElement == null || !(implementsElement instanceof PsiClass)) continue;

      if (((PsiClass) implementsElement).isInterface()) {
        interfaces.add(implementsReferenceElement);
      } else {
        classes.add(implementsReferenceElement);
      }
    }

    if (myExtendsClause != null) {
      final ASTNode extendsClauseNode = myExtendsClause.getNode();
      extendsClauseNode.getTreeParent().removeChild(extendsClauseNode);
    }

    if (myImplementsClause != null) {
      final ASTNode implClauseNode = myImplementsClause.getNode();
      implClauseNode.getTreeParent().removeChild(implClauseNode);
    }

    if (!classes.isEmpty()) {
      addNewExtendsClause(classes, project);
    }

    if (!interfaces.isEmpty()) {
      addNewImplementsClause(interfaces, project);
    }

    CodeStyleManager.getInstance(project).reformatText(myClass.getContainingFile(),
        myClass.getTextRange().getStartOffset(), myClass.getBody().getTextRange().getStartOffset());
  }

  private void addNewExtendsClause(List<GrCodeReferenceElement> elements, Project project) throws IncorrectOperationException {
    String classText = "class A extends ";

    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) classText += ", ";

      GrCodeReferenceElement element = elements.get(i);
      classText += element.getCanonicalText();
    }

    classText += "{}";
    GrExtendsClause newExtendsClause = GroovyElementFactory.getInstance(project).createTypeDefinition(classText).getExtendsClause();

    assert newExtendsClause != null;
    myClass.getNode().addChild(newExtendsClause.getNode(), myClass.getBody().getNode());
  }

  private void addNewImplementsClause(List<GrCodeReferenceElement> elements, Project project) throws IncorrectOperationException {
    String classText = "class A implements ";

    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) classText += ", ";

      GrCodeReferenceElement element = elements.get(i);
      classText += element.getCanonicalText();
    }

    classText += "{}";
    GrImplementsClause newImplementsClause = GroovyElementFactory.getInstance(project).createTypeDefinition(classText).getImplementsClause();

    assert newImplementsClause != null;
    myClass.getNode().addChild(newImplementsClause.getNode(), myClass.getBody().getNode());
  }

  public boolean startInWriteAction() {
    return true;
  }
}
