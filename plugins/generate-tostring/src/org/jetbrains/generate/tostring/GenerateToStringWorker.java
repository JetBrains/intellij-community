/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package org.jetbrains.generate.tostring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.config.*;
import org.jetbrains.generate.tostring.element.*;
import org.jetbrains.generate.tostring.exception.GenerateCodeException;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.velocity.VelocityFactory;
import org.jetbrains.generate.tostring.view.MethodExistsDialog;

import java.io.StringWriter;
import java.util.*;

public class GenerateToStringWorker {
  private static final Logger logger = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringWorker");

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

  public void execute(Collection<PsiMember> members, TemplateResource template) throws IncorrectOperationException, GenerateCodeException {
    // decide what to do if the method already exists
    ConflictResolutionPolicy resolutionPolicy = exitsMethodDialog(template);
    // what insert policy should we use?
    resolutionPolicy.setNewMethodStrategy(getStrategy(config.getInsertNewMethodInitialOption()));

    // user didn't click cancel so go on
    Map<String, String> params = new HashMap<String, String>();

    // before
    beforeCreateToStringMethod(params, template);

    // generate method
    PsiMethod method = createToStringMethod(members, resolutionPolicy, params, template);

    // after, if method was generated (not cancel policy)
    if (method != null) {
      afterCreateToStringMethod(method, params, template);
    }
  }

  private static InsertNewMethodStrategy getStrategy(InsertWhere option) {
    switch (option) {
      case AFTER_EQUALS_AND_HASHCODE:
        return InsertAfterEqualsHashCodeStrategy.getInstance();
      case AT_CARET:
        return InsertAtCaretStrategy.getInstance();
      case AT_THE_END_OF_A_CLASS:
        return InsertLastStrategy.getInstance();
    }

    return InsertLastStrategy.getInstance();
  }

  /**
   * This method gets the choice if there is an existing <code>toString</code> method.
   * <br/> 1) If there is a settings to always override use this.
   * <br/> 2) Prompt a dialog and let the user decide.
   *
   * @param template the chosen template to use
   * @return the policy the user selected (never null)
   */
  private ConflictResolutionPolicy exitsMethodDialog(TemplateResource template) {
    final DuplicationPolicy dupPolicy = config.getReplaceDialogInitialOption();
    if (dupPolicy == DuplicationPolicy.ASK) {
      PsiMethod existingMethod = PsiAdapter.findMethodByName(clazz, template.getTargetMethodName());
      if (existingMethod != null) {
        return MethodExistsDialog.showDialog(template.getTargetMethodName());
      }
    }
    else if (dupPolicy == DuplicationPolicy.REPLACE) {
      return ReplacePolicy.getInstance();
    }

    // If there is no conflict, duplicate policy will do the trick
    return DuplicatePolicy.getInstance();
  }

  /**
   * This method is executed just before the <code>toString</code> method is created or updated.
   *
   * @param params   additional parameters stored with key/value in the map.
   * @param template the template to use
   */
  private void beforeCreateToStringMethod(Map<String, String> params, TemplateResource template) {
    PsiMethod existingMethod = PsiAdapter.findMethodByName(clazz, template.getTargetMethodName()); // find the existing method
    if (existingMethod != null && existingMethod.getDocComment() != null) {
      PsiDocComment doc = existingMethod.getDocComment();
      if (doc != null) {
        params.put("existingJavaDoc", doc.getText());
      }
    }
  }

  /**
   * Creates the <code>toString</code> method.
   *
   * @param selectedMembers the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
   * @param policy          conflict resolution policy
   * @param params          additional parameters stored with key/value in the map.
   * @param template        the template to use
   * @return the created method, null if the method is not created due the user cancels this operation
   * @throws GenerateCodeException       is thrown when there is an error generating the javacode.
   * @throws IncorrectOperationException is thrown by IDEA.
   */
  @Nullable
  private PsiMethod createToStringMethod(Collection<PsiMember> selectedMembers,
                                         ConflictResolutionPolicy policy,
                                         Map<String, String> params,
                                         TemplateResource template) throws IncorrectOperationException, GenerateCodeException {
    // generate code using velocity
    String body = velocityGenerateCode(selectedMembers, params, template.getMethodBody());
    if (logger.isDebugEnabled()) logger.debug("Method body generated from Velocity:\n" + body);

    // fix weird linebreak problem in IDEA #3296 and later
    body = StringUtil.convertLineSeparators(body);

    // create psi newMethod named toString()
    final JVMElementFactory topLevelFactory = JVMElementFactories.getFactory(clazz.getLanguage(), clazz.getProject());
    PsiMethod newMethod = topLevelFactory.createMethodFromText(template.getMethodSignature() + " { " + body + " }", clazz);
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
      newJavaDoc = velocityGenerateCode(selectedMembers, params, newJavaDoc);
      if (logger.isDebugEnabled()) logger.debug("JavaDoc body generated from Velocity:\n" + newJavaDoc);

      applyJavaDoc(toStringMethod, existingJavaDoc, newJavaDoc);
    }

    // return the created method
    return toStringMethod;
  }

  private static void applyJavaDoc(PsiMethod newMethod, String existingJavaDoc, String newJavaDoc) {
    String text = newJavaDoc != null ? newJavaDoc : existingJavaDoc; // prefer to use new javadoc
    PsiAdapter.addOrReplaceJavadoc(newMethod, text, true);
  }


  /**
   * This method is executed just after the <code>toString</code> method is created or updated.
   *
   * @param method   the newly created/updated <code>toString</code> method.
   * @param params   additional parameters stored with key/value in the map.
   * @param template the template to use
   * @throws IncorrectOperationException is thrown by IDEA
   */
  private void afterCreateToStringMethod(PsiMethod method, Map<String, String> params, TemplateResource template) {
    PsiFile containingFile = clazz.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
      if (params.get("autoImportPackages") != null) {
        // keep this for old user templates
        autoImportPackages(javaFile, params.get("autoImportPackages"));
      }
      method = (PsiMethod)JavaCodeStyleManager.getInstance(clazz.getProject()).shortenClassReferences(method);
    }

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

  /**
   * Generates the code using Velocity.
   * <p/>
   * This is used to create the <code>toString</code> method body and it's javadoc.
   *
   * @param selectedMembers the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
   * @param params          additional parameters stored with key/value in the map.
   * @param templateMacro   the velocity macro template
   * @return code (usually javacode). Returns null if templateMacro is null.
   * @throws GenerateCodeException is thrown when there is an error generating the javacode.
   */
  private String velocityGenerateCode(Collection<PsiMember> selectedMembers, Map<String, String> params, String templateMacro)
    throws GenerateCodeException {
    if (templateMacro == null) {
      return null;
    }

    StringWriter sw = new StringWriter();
    try {
      VelocityContext vc = new VelocityContext();

      vc.put("java_version", PsiAdapter.getJavaVersion(clazz));

      // field information
      logger.debug("Velocity Context - adding fields");
      vc.put("fields", ElementUtils.getOnlyAsFieldElements(selectedMembers));

      // method information
      logger.debug("Velocity Context - adding methods");
      vc.put("methods", ElementUtils.getOnlyAsMethodElements(selectedMembers));

      // element information (both fields and methods)
      logger.debug("Velocity Context - adding members (fields and methods)");
      List<Element> elements = ElementUtils.getOnlyAsFieldAndMethodElements(selectedMembers);
      // sort elements if enabled and not using chooser dialog
      if (config.getSortElements() != 0) {
        Collections.sort(elements, new ElementComparator(config.getSortElements()));
      }
      vc.put("members", elements);

      // class information
      ClassElement ce = ElementFactory.newClassElement(clazz);
      vc.put("class", ce);
      if (logger.isDebugEnabled()) logger.debug("Velocity Context - adding class: " + ce);

      // information to keep as it is to avoid breaking compatibility with prior releases
      vc.put("classname", config.isUseFullyQualifiedName() ? ce.getQualifiedName() : ce.getName());
      vc.put("FQClassname", ce.getQualifiedName());

      if (logger.isDebugEnabled()) logger.debug("Velocity Macro:\n" + templateMacro);

      // velocity
      VelocityEngine velocity = VelocityFactory.getVelocityEngine();
      logger.debug("Executing velocity +++ START +++");
      velocity.evaluate(vc, sw, this.getClass().getName(), templateMacro);
      logger.debug("Executing velocity +++ END +++");

      // any additional packages to import returned from velocity?
      if (vc.get("autoImportPackages") != null) {
        params.put("autoImportPackages", (String)vc.get("autoImportPackages"));
      }
    }
    catch (Exception e) {
      throw new GenerateCodeException("Error in Velocity code generator", e);
    }

    return sw.getBuffer().toString();
  }

  /**
   * Generates the toString() code for the specified class and selected
   * fields, doing the work through a WriteAction ran by a CommandProcessor.
   *
   * @param selectedMembers list of members selected
   * @param template         the chosen template to use
   * @param insertAtOverride
   */
  public static void executeGenerateActionLater(final PsiClass clazz,
                                                final Editor editor,
                                                final Collection<PsiMember> selectedMembers,
                                                final TemplateResource template,
                                                final boolean insertAtOverride) {
    Runnable writeCommand = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              new GenerateToStringWorker(clazz, editor, insertAtOverride).execute(selectedMembers, template);
            }
            catch (Exception e) {
              GenerateToStringUtils.handleException(clazz.getProject(), e);
            }
          }
        });
      }
    };

    CommandProcessor.getInstance().executeCommand(clazz.getProject(), writeCommand, "GenerateToString", null);
  }
}
