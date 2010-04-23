/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.config.*;
import org.jetbrains.generate.tostring.element.*;
import org.jetbrains.generate.tostring.exception.GenerateCodeException;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.util.StringUtil;
import org.jetbrains.generate.tostring.velocity.VelocityFactory;
import org.jetbrains.generate.tostring.view.MethodExistsDialog;

import java.io.StringWriter;
import java.util.*;

public class GenerateToStringWorker {
  private static final Logger logger = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringWorker");

  private final PsiElementFactory elementFactory;
  private final CodeStyleManager codeStyleManager;
  private final Editor editor;
  private final PsiFile containingFile;
  private final PsiClass clazz;
  private final PsiAdapter psi;
  private final Config config;
  private final Project project;
  private final boolean hasOverrideAnnotation;

  public GenerateToStringWorker(PsiClass clazz, Editor editor, boolean insertAtOverride) {
    this.clazz = clazz;
    this.project = clazz.getProject();
    this.psi = PsiAdapterFactory.getPsiAdapter();
    this.editor = editor;
    this.elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    this.codeStyleManager = CodeStyleManager.getInstance(project);
    this.containingFile = clazz.getContainingFile();
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
   * This method get's the choice if there is an existing <code>toString</code> method.
   * <br/> 1) If there is a settings to always override use this.
   * <br/> 2) Prompt a dialog and let the user decide.
   *
   * @param template the choosen template to use
   * @return the policy the user selected (never null)
   */
  private ConflictResolutionPolicy exitsMethodDialog(TemplateResource template) {
    final DuplicatonPolicy dupPolicy = config.getReplaceDialogInitialOption();
    if (dupPolicy == DuplicatonPolicy.ASK) {
      PsiMethod existingMethod = psi.findMethodByName(clazz, template.getTargetMethodName());
      if (existingMethod != null) {
        return MethodExistsDialog.showDialog(template.getTargetMethodName());
      }
    }
    else if (dupPolicy == DuplicatonPolicy.REPLACE) {
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
    PsiMethod existingMethod = psi.findMethodByName(clazz, template.getTargetMethodName()); // find the existing method
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
    body = StringUtil.fixLineBreaks(body);

    // create psi newMethod named toString()
    PsiMethod newMethod = elementFactory.createMethodFromText(template.getMethodSignature() + " { " + body + " }", null);
    codeStyleManager.reformat(newMethod);

    // insertNewMethod conflict resolution policy (add/replace, duplicate, cancel)
    PsiMethod existingMethod = clazz.findMethodBySignature(newMethod, false);
    PsiMethod toStringMethod = policy.applyMethod(clazz, existingMethod, newMethod, editor);
    if (toStringMethod == null) {
      return null; // user cancelled so return null
    }

    if (hasOverrideAnnotation) {
      annotate(toStringMethod, "java.lang.Override");
    }

    // add annotations
    if (template.hasAnnotations()) {
      String[] annotations = template.getAnnotations();
      // must reverse loop to add annotations in the same order as in the template (when inserting it would insert in top)
      for (int i = annotations.length - 1; i > -1; i--) {
        String text = annotations[i];
        psi.addAnnotationToMethod(elementFactory, toStringMethod, text);
      }
    }

    // applyJavaDoc conflict resolution policy (add or keep existing)
    String existingJavaDoc = params.get("existingJavaDoc");
    String newJavaDoc = template.getJavaDoc();
    if (existingJavaDoc != null || newJavaDoc != null) {
      // generate javadoc using velocity
      newJavaDoc = velocityGenerateCode(selectedMembers, params, newJavaDoc);
      if (logger.isDebugEnabled()) logger.debug("JavaDoc body generated from Velocity:\n" + newJavaDoc);

      applyJavaDoc(toStringMethod, elementFactory, codeStyleManager, existingJavaDoc, newJavaDoc);
    }

    // return the created method
    return toStringMethod;
  }

  private void applyJavaDoc(PsiMethod newMethod,
                            PsiElementFactory elementFactory,
                            CodeStyleManager codeStyleManager,
                            String existingJavaDoc,
                            String newJavaDoc) throws IncorrectOperationException {
    String text = newJavaDoc != null ? newJavaDoc : existingJavaDoc; // prefer to use new javadoc
    psi.addOrReplaceJavadoc(elementFactory, codeStyleManager, newMethod, text, true);
  }


  /**
   * This method is executed just after the <code>toString</code> method is created or updated.
   *
   * @param method   the newly created/updated <code>toString</code> method.
   * @param params   additional parameters stored with key/value in the map.
   * @param template the template to use
   * @throws IncorrectOperationException is thrown by IDEA
   */
  private void afterCreateToStringMethod(PsiMethod method, Map<String, String> params, TemplateResource template)
    throws IncorrectOperationException {

    if (containingFile instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
      // if the code uses Arrays, then make sure java.util.Arrays is imported.
      String javaCode = method.getText();
      if (javaCode.indexOf("Arrays.") > 0 &&
          !(psi.hasImportStatement(javaFile, "java.util.*") || psi.hasImportStatement(javaFile, "java.util.Arrays"))) {
        // java.util.Arrays must be imported as java.util.* since the addImportStatement method doens't support onDemand-import statement yet.
        psi.addImportStatement(javaFile, "java.util.*", elementFactory);
      }

      // if the code uses Reflection (Field[]), then make sure java.lang.reflect.Field is imported.
      if (javaCode.indexOf("Field[]") > 0 &&
          !(psi.hasImportStatement(javaFile, "java.lang.reflect.*") || psi.hasImportStatement(javaFile, "java.lang.reflect.Field"))) {
        // java.lang.reflect.Field must be imported as java.lang.reflect.* since the addImportStatement method doens't support onDemand-import statement yet.
        psi.addImportStatement(javaFile, "java.lang.reflect.*", elementFactory);
      }

      // any additional packages to import from the params
      if (params.get("autoImportPackages") != null) {
        autoImportPackages(javaFile, params.get("autoImportPackages"));
      }
    }

    // reformat code
    codeStyleManager.reformat(method);

    // jump to method
    if (config.isJumpToMethod() && editor != null) {
      PsiMethod newMethod = psi.findMethodByName(clazz, template.getTargetMethodName());
      if (newMethod != null) {
        int offset = newMethod.getTextOffset();
        if (offset > 2) {
          VisualPosition vp = editor.offsetToVisualPosition(offset);
          if (logger.isDebugEnabled()) logger.debug("Moving/Scrolling caret to " + vp + " (offset=" + offset + ")");
          editor.getCaretModel().moveToVisualPosition(vp);
          editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_DOWN);
        }
      }
    }
  }

  /**
   * Automatic import the packages.
   *
   * @param packageNames names of packages (must end with .* and be seperated by ; or ,)
   * @throws IncorrectOperationException error adding imported package
   */
  private void autoImportPackages(PsiJavaFile psiJavaFile, String packageNames) throws IncorrectOperationException {
    StringTokenizer tok = new StringTokenizer(packageNames, ",");
    while (tok.hasMoreTokens()) {
      String packageName = tok.nextToken().trim(); // trim in case of space
      if (logger.isDebugEnabled()) logger.debug("Auto importing package: " + packageName);
      psi.addImportStatement(psiJavaFile, packageName, elementFactory);
    }
  }

  /**
   * Generates the code using Velocity.
   * <p/>
   * This is used to create the <code>toString</code> method body and it's javadoc.
   *
   * @param selectedMembers the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
   * @param params          additional parameters stored with key/value in the map.
   * @param templateMacro   the veloicty macro template
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

      // field information
      logger.debug("Velocity Context - adding fields");
      vc.put("fields", ElementUtils.getOnlyAsFieldElements(project, psi, selectedMembers));

      // method information
      logger.debug("Velocity Context - adding methods");
      vc.put("methods", ElementUtils.getOnlyAsMethodElements(elementFactory, psi, selectedMembers));

      // element information (both fields and methods)
      logger.debug("Velocity Context - adding members (fields and methods)");
      List<Element> elements = ElementUtils.getOnlyAsFieldAndMethodElements(project, elementFactory, psi, selectedMembers);
      // sort elements if enabled and not using chooser dialog
      if (config.getSortElements() != 0) {
        Collections.sort(elements, new ElementComparator(config.getSortElements()));
      }
      vc.put("members", elements);

      // class information
      ClassElement ce = ElementFactory.newClassElement(project, clazz, psi);
      vc.put("class", ce);
      if (logger.isDebugEnabled()) logger.debug("Velocity Context - adding class: " + ce);

      // information to keep as it is to avoid breaking compability with prior releases
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

      // add java.io.Serializable if choosen in [settings] and does not already implements it
      if (config.isAddImplementSerializable() && !ce.isImplements("java.io.Serializable")) {
        psi.addImplements(project, clazz, "java.io.Serializable");
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
   * @param selectedMemebers list of members selected
   * @param template         the choosen template to use
   * @param insertAtOverride
   */
  public static void executeGenerateActionLater(final PsiClass clazz,
                                                final Editor editor,
                                                final Collection<PsiMember> selectedMemebers,
                                                final TemplateResource template,
                                                final boolean insertAtOverride) {
    Runnable writeCommand = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              new GenerateToStringWorker(clazz, editor, insertAtOverride).execute(selectedMemebers, template);
            }
            catch (Exception e) {
              GenerateToStringUtils.handleExeption(clazz.getProject(), e);
            }
          }
        });
      }
    };

    PsiAdapterFactory.getPsiAdapter().executeCommand(clazz.getProject(), writeCommand);
  }

  private static void annotate(final PsiMethod result, String fqn) throws IncorrectOperationException {
    Project project = result.getProject();
    AddAnnotationFix fix = new AddAnnotationFix(fqn, result);
    if (fix.isAvailable(project, null, result.getContainingFile())) {
      fix.invoke(project, null, result.getContainingFile());
    }
  }
}