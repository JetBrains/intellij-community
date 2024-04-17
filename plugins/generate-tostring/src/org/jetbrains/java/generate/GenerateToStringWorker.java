// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package org.jetbrains.java.generate;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.config.*;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.psi.PsiAdapter;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.view.MethodExistsDialog;

import java.util.*;

public class GenerateToStringWorker {
  private static final Logger logger = Logger.getInstance(GenerateToStringWorker.class);

  private final Editor editor;
  private final PsiClass clazz;
  private final Config config;
  private final boolean hasOverrideAnnotation;

  public GenerateToStringWorker(PsiClass clazz, Editor editor, boolean insertAtOverride) {
    this.clazz = clazz;
    this.editor = editor;
    this.config = GenerateToStringContext.getConfig();
    this.hasOverrideAnnotation = insertAtOverride;
  }

  /**
   * Creates the {@code toString} method.
   *
   * @param selectedMembers the selected members as both {@link PsiField} and {@link PsiMethod}.
   * @param policy          conflict resolution policy
   * @param params          additional parameters stored with key/value in the map.
   * @param template        the template to use
   * @return the created method, null if the method is not created due the user cancels this operation
   * @throws GenerateCodeException       is thrown when there is an error generating the javacode.
   * @throws IncorrectOperationException is thrown by IDEA.
   */
  private @Nullable PsiMethod createToStringMethod(Collection<PsiMember> selectedMembers,
                                         ConflictResolutionPolicy policy,
                                         Map<String, String> params,
                                         TemplateResource template) throws IncorrectOperationException, GenerateCodeException {
    PsiMethod newMethod = getMethodPrototype(selectedMembers, params, template);
    if (newMethod == null) return null;

    CodeStyleManager.getInstance(clazz.getProject()).reformat(newMethod);

    // insertNewMethod conflict resolution policy (add/replace, duplicate, cancel)
    PsiMethod existingMethod = clazz.findMethodBySignature(newMethod, false);
    PsiMethod toStringMethod = policy.applyMethod(clazz, existingMethod, newMethod, editor);
    if (toStringMethod == null) {
      return null; // user cancelled so return null
    }

    if (hasOverrideAnnotation) {
      toStringMethod.getModifierList().addAnnotation("java.lang.Override");
    }

    // applyJavaDoc conflict resolution policy (add or keep existing)
    String existingJavaDoc = params.get("existingJavaDoc");
    String newJavaDoc = template.getJavaDoc();
    if (existingJavaDoc != null || newJavaDoc != null) {
      // generate javadoc using velocity
      newJavaDoc = GenerationUtil.velocityGenerateCode(clazz, selectedMembers, params, newJavaDoc, config.getSortElements(), config.isUseFullyQualifiedName());
      if (logger.isDebugEnabled()) logger.debug("JavaDoc body generated from Velocity:\n" + newJavaDoc);

      GenerationUtil.applyJavaDoc(toStringMethod, existingJavaDoc, newJavaDoc);
    }

    // return the created method
    return toStringMethod;
  }

  private PsiMethod getMethodPrototype(Collection<PsiMember> selectedMembers, Map<String, String> params, TemplateResource template) {
    String evaluatedText = GenerationUtil.velocityGenerateCode(clazz,
                                                               selectedMembers,
                                                               Set.of(),
                                                               params,
                                                               Map.of(),
                                                               template.getTemplate(),
                                                               config.getSortElements(),
                                                               config.isUseFullyQualifiedName(),
                                                               config.isEnableMethods());
    final JVMElementFactory topLevelFactory = JVMElementFactories.getFactory(clazz.getLanguage(), clazz.getProject());
    if (topLevelFactory == null) {
      return null;
    }
    try {
      return topLevelFactory.createMethodFromText(evaluatedText, clazz);
    }
    catch (IncorrectOperationException e) {
      logger.info(e);
      HintManager.getInstance().showErrorHint(editor, JavaBundle
        .message("hint.text.tostring.method.could.not.be.created.from.template", template.getFileName()));
      return null;
    }
  }

  public void execute(Collection<PsiMember> members, TemplateResource template, final ConflictResolutionPolicy resolutionPolicy) throws IncorrectOperationException, GenerateCodeException {
    // what insert policy should we use?
    resolutionPolicy.setNewMethodStrategy(getStrategy(config.getInsertNewMethodInitialOption()));

    // user didn't click cancel so go on
    Map<String, String> params = new HashMap<>();

    // before
    beforeCreateToStringMethod(params, template);

    // generate method
    PsiMethod method =
      createToStringMethod(members, resolutionPolicy, params, template);

    // after, if method was generated (not cancel policy)
    if (method != null) {
      afterCreateToStringMethod(method, params, template);
    }
  }

  private static InsertNewMethodStrategy getStrategy(InsertWhere option) {
    return switch (option) {
      case AFTER_EQUALS_AND_HASHCODE -> InsertAfterEqualsHashCodeStrategy.getInstance();
      case AT_CARET -> InsertAtCaretStrategy.getInstance();
      case AT_THE_END_OF_A_CLASS -> InsertLastStrategy.getInstance();
    };
  }

  /**
   * This method gets the choice if there is an existing {@code toString} method.
   * <br/> 1) If there is a settings to always override use this.
   * <br/> 2) Prompt a dialog and let the user decide.
   *
   * @param template the chosen template to use
   * @return the policy the user selected (never null)
   */
  protected ConflictResolutionPolicy exitsMethodDialog(TemplateResource template) {
    final DuplicationPolicy dupPolicy = config.getReplaceDialogInitialOption();
    if (dupPolicy == DuplicationPolicy.ASK) {
      PsiMethod targetMethod = getMethodPrototype(Collections.emptyList(), Collections.emptyMap(), template);
      PsiMethod existingMethod = targetMethod != null ? clazz.findMethodBySignature(targetMethod, false) : null;
      if (existingMethod != null) {
        return MethodExistsDialog.showDialog(targetMethod.getName());
      }
    }
    else if (dupPolicy == DuplicationPolicy.REPLACE) {
      return ReplacePolicy.getInstance();
    }

    // If there is no conflict, duplicate policy will do the trick
    return DuplicatePolicy.getInstance();
  }

  /**
   * This method is executed just before the {@code toString} method is created or updated.
   *
   * @param params   additional parameters stored with key/value in the map.
   * @param template the template to use
   */
  private void beforeCreateToStringMethod(Map<String, String> params, TemplateResource template) {
    PsiMethod targetMethod = getMethodPrototype(Collections.emptyList(), Collections.emptyMap(), template);
    if (targetMethod == null) return;
    PsiMethod existingMethod = clazz.findMethodBySignature(targetMethod, false); // find the existing method
    if (existingMethod != null && existingMethod.getDocComment() != null) {
      PsiDocComment doc = existingMethod.getDocComment();
      if (doc != null) {
        params.put("existingJavaDoc", doc.getText());
      }
    }
  }


  /**
   * This method is executed just after the {@code toString} method is created or updated.
   *
   * @param method   the newly created/updated {@code toString} method.
   * @param params   additional parameters stored with key/value in the map.
   * @param template the template to use
   * @throws IncorrectOperationException is thrown by IDEA
   */
  private void afterCreateToStringMethod(PsiMethod method, Map<String, String> params, TemplateResource template) {
    PsiFile containingFile = clazz.getContainingFile();
    if (containingFile instanceof PsiJavaFile javaFile) {
      if (params.get("autoImportPackages") != null) {
        // keep this for old user templates
        autoImportPackages(javaFile, params.get("autoImportPackages"));
      }
    }
    method = (PsiMethod)JavaCodeStyleManager.getInstance(clazz.getProject()).shortenClassReferences(method);

    // jump to method
    if (!config.isJumpToMethod() || editor == null) {
      return;
    }
    int offset = method.getTextOffset();
    if (offset <= 2) {
      return;
    }
    VisualPosition vp = editor.offsetToVisualPosition(offset);
    if (logger.isDebugEnabled()) logger.debug("Moving/Scrolling caret to " + vp + " (offset=" + offset + ")");
    editor.getCaretModel().moveToVisualPosition(vp);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_DOWN);
  }

  /**
   * Automatic import the packages.
   *
   * @param packageNames names of packages (must end with .* and be separated by ; or ,)
   * @throws IncorrectOperationException error adding imported package
   */
  private static void autoImportPackages(PsiJavaFile psiJavaFile, String packageNames) throws IncorrectOperationException {
    StringTokenizer tok = new StringTokenizer(packageNames, ",");
    while (tok.hasMoreTokens()) {
      String packageName = tok.nextToken().trim(); // trim in case of space
      if (logger.isDebugEnabled()) logger.debug("Auto importing package: " + packageName);
      PsiAdapter.addImportStatement(psiJavaFile, packageName);
    }
  }
}
