package org.jetbrains.plugins.groovy.override;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageCodeInsightActionHandler;
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
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;

import java.io.IOException;
import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 11.09.2007
 */
public class OverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
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

    List<PsiMethodMember> classMembers = new ArrayList<PsiMethodMember>();
    Collection<CandidateInfo> candidates = OverrideImplementUtil.getMethodsToOverrideImplement(aClass, false);
    for (CandidateInfo candidate : candidates) {
      classMembers.add(new PsiMethodMember(candidate));
    }

    MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(classMembers.toArray(new PsiMethodMember[classMembers.size()]), false, true, project);
    chooser.show();

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.size() == 0) return;

    for (PsiMethodMember overridenMethodMember : selectedElements) {
      final PsiMethod originalMethod = overridenMethodMember.getElement();

      String templName = originalMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
          "Implemented Method Body.java" : "Overridden Method Body.java";
//
//      String templName = FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;

      final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
      final GrMethod result = createOverridingMethodSignature(project, originalMethod);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            result.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, aClass.isInterface());
            result.getModifierList().setModifierProperty(PsiModifier.NATIVE, false);

            doWriteOverridingMethod(project, originalMethod, result, template);

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

//    @NotNull
//  private static String callSuper (PsiMethod superMethod, PsiMethod overriding) {
//    @NonNls StringBuilder buffer = new StringBuilder();
//    if (!superMethod.isConstructor() && superMethod.getReturnType() != PsiType.VOID) {
//      buffer.append("return ");
//    }
//    buffer.append("super");
//    PsiParameter[] parms = overriding.getParameterList().getParameters();
//    if (!superMethod.isConstructor()){
//      buffer.append(".");
//      buffer.append(superMethod.getName());
//    }
//    buffer.append("(");
//    for (int i = 0; i < parms.length; i++) {
//      String name = parms[i].getName();
//      if (i > 0) buffer.append(",");
//      buffer.append(name);
//    }
//    buffer.append(")");
//    return buffer.toString();
//  }
//
//  public static void setupMethodBody(GrMethod result, GrMethod originalMethod, PsiClass targetClass) throws IncorrectOperationException {
//    String templName = originalMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
//                       FileTemplateManager.TEMPLATE_IMPLEMENTED_METHOD_BODY : FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;
//    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
//    setupMethodBody(result, originalMethod, targetClass, template);
//  }
//
//  public static void setupMethodBody(final GrMethod result, final GrMethod originalMethod, final PsiClass targetClass,
//                                     final FileTemplate template) throws IncorrectOperationException {
//    if (targetClass.isInterface()) {
//      final GrOpenBlock body = result.getBlock();
//      if (body != null) body.delete();
//    }
//
//    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
//    PsiType returnType = result.getReturnType();
//    if (returnType == null) {
//      returnType = PsiType.VOID;
//    }
//    Properties properties = new Properties();
//    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
//    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
//    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(originalMethod, result));
//    FileTemplateUtil.setClassAndMethodNameProperties(properties, targetClass, result);
//
//    PsiElementFactory factory = originalMethod.getManager().getElementFactory();
//    @NonNls String methodText;
//    try {
//      String bodyText = template.getText(properties);
//      if (!"".equals(bodyText)) bodyText += "\n";
//      methodText = "void foo () {\n" + bodyText + "}";
//      methodText = FileTemplateUtil.indent(methodText, result.getProject(), fileType);
//    } catch (Exception e) {
//      throw new IncorrectOperationException("Failed to parse file template",e);
//    }
//    if (methodText != null) {
//      PsiMethod m;
//      try {
//        m = factory.createMethodFromText(methodText, originalMethod);
//      }
//      catch (IncorrectOperationException e) {
//        ApplicationManager.getApplication().invokeLater(new Runnable() {
//          public void run() {
//            Messages.showErrorDialog(CodeInsightBundle.message("override.implement.broken.file.template.message"),
//                                     CodeInsightBundle.message("override.implement.broken.file.template.title"));
//          }
//        });
//        return;
//      }
//      PsiCodeBlock oldBody = result.getBody();
//      if (oldBody != null) {
//        oldBody.replace(m.getBody());
//      }
//    }
//  }

  private boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
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


  private GrMethod createOverridingMethodSignature(Project project, PsiMethod method) {
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

  private void doWriteOverridingMethod(Project project, PsiMethod method, GrMethod result, FileTemplate template) {
    final PsiType returnType = method.getReturnType();
    Properties properties = new Properties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
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

  public boolean startInWriteAction() {
    return true;
  }
}
