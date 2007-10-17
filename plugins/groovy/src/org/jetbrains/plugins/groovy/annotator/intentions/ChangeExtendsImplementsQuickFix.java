package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.09.2007
 */
public class ChangeExtendsImplementsQuickFix implements IntentionAction {
  @Nullable
  private final GrExtendsClause myExtendsClause;
  @Nullable
  private final GrImplementsClause myImplementsClause;

  final GrTypeDefinition myClass;

  public ChangeExtendsImplementsQuickFix(GrExtendsClause extendsClause, GrImplementsClause implementsClause) {
    myExtendsClause = extendsClause;
    myImplementsClause = implementsClause;

    if (myImplementsClause != null) {
      myClass = (GrTypeDefinition) myImplementsClause.getParent();
    } else {
      assert myExtendsClause != null;
      myClass = (GrTypeDefinition) myExtendsClause.getParent();
    }
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

    Set<String> classes = new HashSet<String>();
    Set<String> interfaces = new HashSet<String>();

    for (GrCodeReferenceElement extendsReferenceElement : extendsReferenceElements) {
      final PsiElement extendsElement = extendsReferenceElement.resolve();
      if (!(extendsElement instanceof PsiClass)) continue;

      if (myClass.isInterface() && !((PsiClass) extendsElement).isInterface()) continue;

      if (myClass.isInterface()) {
        if (((PsiClass) extendsElement).isInterface()) {
          classes.add(extendsReferenceElement.getCanonicalText());
        }
      } else {
        if (((PsiClass) extendsElement).isInterface()) {
          interfaces.add(extendsReferenceElement.getCanonicalText());
        } else {
          classes.add(extendsReferenceElement.getCanonicalText());
        }
      }
//      if (((PsiClass) extendsElement).isInterface()) {
//        interfaces.add(extendsReferenceElement.getCanonicalText());
//      } else {
//        classes.add(extendsReferenceElement.getCanonicalText());
//      }
    }

    for (GrCodeReferenceElement implementsReferenceElement : implementsReferenceElements) {
      //interface cannot implement anything

      final PsiElement implementsElement = implementsReferenceElement.resolve();
      if (!(implementsElement instanceof PsiClass)) continue;

      if (myClass.isInterface()) {
        if (((PsiClass) implementsElement).isInterface()) {
          classes.add(implementsReferenceElement.getCanonicalText());
        }
      } else {
        if (((PsiClass) implementsElement).isInterface()) {
          interfaces.add(implementsReferenceElement.getCanonicalText());
        } else {
          classes.add(implementsReferenceElement.getCanonicalText());
        }
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
      addNewClause(classes, project, true);
    }

    if (!interfaces.isEmpty()) {
      addNewClause(interfaces, project, false);
    }

    CodeStyleManager.getInstance(project).reformatText(myClass.getContainingFile(),
        myClass.getTextRange().getStartOffset(), myClass.getBody().getTextOffset() + 2);
  }

  private void addNewClause(Set<String> elements, Project project, boolean isExtends) throws IncorrectOperationException {
    String classText = "class A " + (isExtends ? "extends " : "implements ");

    boolean first = true;
    for (String str : elements) {
      if (!first) classText += ", ";
      classText += str;
      first = false;
    }

//      for (int i = 0; i < elements.size(); i++) {
//            if (i > 0) classText += ", ";
//
//            classText += elements.get(i);
//        }

    classText += " {}";

    final GrTypeDefinition definition = GroovyElementFactory.getInstance(project).createTypeDefinition(classText);
    GroovyPsiElement clause = isExtends ? definition.getExtendsClause() : definition.getImplementsClause();

    assert clause != null;
    myClass.getNode().addChild(clause.getNode(), myClass.getBody().getNode());
    PsiUtil.shortenReferences(clause);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
