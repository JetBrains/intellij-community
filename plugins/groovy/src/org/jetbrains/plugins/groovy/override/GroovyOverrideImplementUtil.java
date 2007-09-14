package org.jetbrains.plugins.groovy.override;

import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.GroovyBundle;

import java.io.IOException;
import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyOverrideImplementUtil {
  protected static void invokeOverrideImplement(final Project project, Editor editor, PsiFile file, boolean isImplement) {
    assert file instanceof GroovyFileBase;
    ((GroovyFileBase) file).getTypeDefinitions();
    final int offset = editor.getCaretModel().getOffset();

    PsiElement parent = file.findElementAt(offset);
    if (parent == null) return;

    while (!(parent instanceof PsiClass)) {
      parent = parent.getParent();
      if (parent == null) return;
    }

    final PsiClass aClass = (PsiClass) parent;

    if (isImplement && aClass.isInterface()) return;

    List<PsiMethodMember> classMembers = new ArrayList<PsiMethodMember>();
    Collection<CandidateInfo> candidates = com.intellij.codeInsight.generation.OverrideImplementUtil.getMethodsToOverrideImplement(aClass, isImplement);
    for (CandidateInfo candidate : candidates) {
      classMembers.add(new PsiMethodMember(candidate));
    }

    if (classMembers.isEmpty()) return;

    MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(classMembers.toArray(new PsiMethodMember[classMembers.size()]), false, true, project);
    chooser.setTitle(isImplement ? GroovyBundle.message("select.methods.to.override") : GroovyBundle.message("select.methods.to.implement"));
    chooser.show();

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.size() == 0) return;

    for (PsiMethodMember overridenMethodMember : selectedElements) {
      final PsiMethod selectedMethod = overridenMethodMember.getElement();

      final boolean isAbstract = selectedMethod.hasModifierProperty(PsiModifier.ABSTRACT);

//      assert isAbstract == isImplement;
      String templName = isAbstract ? FileTemplateManager.TEMPLATE_IMPLEMENTED_METHOD_BODY : FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;

      final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
      final GrMethod result = createOverrideImplementMethodSignature(project, selectedMethod);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            result.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false/*aClass.isInterface()*/);
            result.getModifierList().setModifierProperty(PsiModifier.NATIVE, false);

            doWriteOverridingMethod(project, selectedMethod, result, template);

            final GrTypeDefinitionBody classBody = ((GrTypeDefinition) aClass).getBody();
            final ASTNode anchor = classBody.getLastChild().getNode();
            final ASTNode lineTerminator = GroovyElementFactory.getInstance(project).createLineTerminator().getNode();

            classBody.getNode().addChild(lineTerminator, anchor);
            classBody.getNode().addChild(result.getNode(), lineTerminator);

            final TextRange textRange = result.getTextRange();

            CodeStyleManager.getInstance(project).reformatText(result.getContainingFile(),
                textRange.getStartOffset(), textRange.getEndOffset());
          } catch (IncorrectOperationException e) {
            e.printStackTrace();
          }
        }
      });

    }
  }

  private static boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    assert modifierList instanceof GrModifierListImpl;
    GrModifierListImpl list = (GrModifierListImpl) modifierList;

    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (list.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private static final String[] GROOVY_MODIFIERS = new String[]{
      PsiModifier.PUBLIC,
      PsiModifier.PROTECTED,
      PsiModifier.PRIVATE,
      PsiModifier.PACKAGE_LOCAL,
      PsiModifier.STATIC,
      PsiModifier.ABSTRACT,
      PsiModifier.FINAL,
      PsiModifier.NATIVE,
      PsiModifier.SYNCHRONIZED,
      PsiModifier.STRICTFP,
      PsiModifier.TRANSIENT,
      PsiModifier.VOLATILE
  };


  private static GrMethod createOverrideImplementMethodSignature(Project project, PsiMethod method) {
    StringBuffer buffer = new StringBuffer();
    writeMethodModifiers(buffer, method.getModifierList(), GROOVY_MODIFIERS);

    final PsiType returnType = method.getReturnType();

    if (returnType != null) {
      buffer.append(returnType.getPresentableText());
      buffer.append(" ");
    }

    buffer.append(method.getName());
    buffer.append(" ");

    buffer.append("(");
    buffer.append(method.getParameterList().getText());
    buffer.append(")");
    buffer.append(" ");

    buffer.append("{");
    buffer.append("}");

    return (GrMethod) GroovyElementFactory.getInstance(project).createTopElementFromText(buffer.toString());
  }

  private static void doWriteOverridingMethod(Project project, PsiMethod method, GrMethod result, FileTemplate template) {
    final PsiType returnType = method.getReturnType();

    String returnTypeText = "";
    if (returnType != null) {
      returnTypeText = returnType.getPresentableText();
    }
    Properties properties = new Properties();

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnTypeText);
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(method, result));
    FileTemplateUtil.setClassAndMethodNameProperties(properties, method.getContainingClass(), result);

    try {
      String bodyText = template.getText(properties);
      final GrCodeBlock newBody = GroovyElementFactory.getInstance(project).createMetodBodyFormText("\n" + bodyText + "\n");
      result.getNode().replaceChild(result.getBlock().getNode(), newBody.getNode());

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @NotNull
  private static String callSuper(PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (!superMethod.isConstructor() && superMethod.getReturnType() != PsiType.VOID) {
      buffer.append("return ");
    }
    buffer.append("super");
    PsiParameter[] parms = overriding.getParameterList().getParameters();
    if (!superMethod.isConstructor()) {
      buffer.append(".");
      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    for (int i = 0; i < parms.length; i++) {
      String name = parms[i].getName();
      if (i > 0) buffer.append(",");
      buffer.append(name);
    }
    buffer.append(")");
    return buffer.toString();
  }
}
