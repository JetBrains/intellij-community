package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsParameterImpl;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil");

  public static void invokeOverrideImplement(final Project project, final Editor editor, final PsiFile file, boolean isImplement) {
    final int offset = editor.getCaretModel().getOffset();

    PsiElement parent = file.findElementAt(offset);
    if (parent == null) return;

    while (!(parent instanceof PsiClass)) {
      parent = parent.getParent();
      if (parent == null) return;
    }

    final PsiClass aClass = (PsiClass) parent;

    if (isImplement && aClass.isInterface()) return;

    Collection<CandidateInfo> candidates = OverrideImplementUtil.getMethodsToOverrideImplement(aClass, isImplement);
    if (candidates.isEmpty()) return;

    List<PsiMethodMember> classMembers = new ArrayList<PsiMethodMember>();
    for (CandidateInfo candidate : candidates) {
      classMembers.add(new PsiMethodMember(candidate));
    }


    MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(classMembers.toArray(new PsiMethodMember[classMembers.size()]), false, true, project);
    chooser.setTitle(isImplement ? GroovyBundle.message("select.methods.to.override") : GroovyBundle.message("select.methods.to.implement"));
    chooser.show();

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.size() == 0) return;

    for (PsiMethodMember methodMember : selectedElements) {
      final PsiMethod method = methodMember.getElement();
      final PsiSubstitutor substitutor = methodMember.getSubstitutor();

      final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

//      assert isAbstract == isImplement;
      String templName = isAbstract ? FileTemplateManager.TEMPLATE_IMPLEMENTED_METHOD_BODY : FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;

      final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
      final GrMethod result = createOverrideImplementMethodSignature(project, method, substitutor, aClass);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            result.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false/*aClass.isInterface()*/);
            result.getModifierList().setModifierProperty(PsiModifier.NATIVE, false);

            setupOverridingMethodBody(project, method, result, template, substitutor);

            final GrTypeDefinitionBody classBody = ((GrTypeDefinition) aClass).getBody();
            final PsiMethod[] methods = aClass.getMethods();

            ASTNode anchor = null;

            final int caretPosition = editor.getCaretModel().getOffset();
            final PsiElement thisCaretPsiElement = file.findElementAt(caretPosition);

            final GrTopLevelDefintion previousTopLevelElement = PsiUtil.findPreviousTopLevelElementByThisElement(thisCaretPsiElement);

            if (thisCaretPsiElement != null && thisCaretPsiElement.getParent() instanceof GrTypeDefinitionBody) {
              anchor = thisCaretPsiElement.getNode();

            } else if (previousTopLevelElement != null && previousTopLevelElement instanceof GrMethod) {
              final PsiElement nextElement = previousTopLevelElement.getNextSibling();
              if (nextElement != null) {
                anchor = nextElement.getNode();
              }
            } else if (methods.length != 0) {
              final PsiMethod lastMethod = methods[methods.length - 1];
              if (lastMethod != null) {
                final PsiElement nextSibling = lastMethod.getNextSibling();
                if (nextSibling != null) {
                  anchor = nextSibling.getNode();
                }
              }

            } else {
              anchor = classBody.getFirstChild().getNextSibling().getNode();
            }

            final ASTNode lineTerminator = GroovyElementFactory.getInstance(project).createLineTerminator().getNode();

            assert lineTerminator != null;
            final ASTNode resultNode = result.getNode();
            assert resultNode != null;

            classBody.getNode().addChild(lineTerminator, anchor);
            classBody.getNode().addChild(resultNode, anchor);

            final TextRange range = result.getTextRange();
            CodeStyleManager.getInstance(project).reformatRange(result.getContainingFile(), range.getStartOffset(), range.getEndOffset());
            PsiUtil.shortenReferences(result);
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });

    }
  }

  private static boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
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


  private static GrMethod createOverrideImplementMethodSignature(Project project, PsiMethod method, PsiSubstitutor substitutor, PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();
    writeMethodModifiers(buffer, method.getModifierList(), GROOVY_MODIFIERS);

    final PsiType returnType = substitutor.substitute(method.getReturnType());

    if (returnType != null) {
      buffer.append(returnType.getCanonicalText());
      buffer.append(" ");
    }

    if (method.isConstructor()) {
      buffer.append(aClass.getName());
    } else {
      buffer.append(method.getName());
    }
    buffer.append(" ");

    buffer.append("(");
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) buffer.append(", ");
      PsiParameter parameter = parameters[i];
      final PsiType parameterType = substitutor.substitute(parameter.getType());
      buffer.append(parameterType.getCanonicalText());
      buffer.append(" ");
      final String paramName = parameter.getName();
      if (paramName != null) {
        buffer.append(paramName);
      } else if (parameter instanceof ClsParameterImpl) {
        final ClsParameterImpl clsParameter = (ClsParameterImpl) parameter;
        buffer.append(((PsiParameter) clsParameter.getMirror()).getName());
      }
    }

    buffer.append(")");
    buffer.append(" ");

    buffer.append("{");
    buffer.append("}");

    return (GrMethod) GroovyElementFactory.getInstance(project).createTopElementFromText(buffer.toString());
  }

  private static void setupOverridingMethodBody(Project project, PsiMethod method, GrMethod result, FileTemplate template, PsiSubstitutor substitutor) {
    final PsiType returnType = substitutor.substitute(method.getReturnType());

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
      LOG.error(e);
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
