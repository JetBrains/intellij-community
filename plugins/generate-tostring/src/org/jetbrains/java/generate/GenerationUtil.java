/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.generate;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.config.ConflictResolutionPolicy;
import org.jetbrains.java.generate.element.*;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.exception.PluginException;
import org.jetbrains.java.generate.psi.PsiAdapter;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.velocity.VelocityFactory;

import java.io.StringWriter;
import java.util.*;

public class GenerationUtil {
  private static final Logger logger = Logger.getInstance("#" + GenerationUtil.class.getName());

  /**
     * Handles any exception during the executing on this plugin.
     *
     * @param project PSI project
     * @param e       the caused exception.
     * @throws RuntimeException is thrown for severe exceptions
     */
    public static void handleException(Project project, Exception e) throws RuntimeException {
        logger.info(e);

        if (e instanceof GenerateCodeException) {
            // code generation error - display velocity error in error dialog so user can identify problem quicker
            Messages.showMessageDialog(project,
                                       "Velocity error generating code - see IDEA log for more details (stacktrace should be in idea.log):\n" +
                                       e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof PluginException) {
            // plugin related error - could be recoverable.
            Messages.showMessageDialog(project, "A PluginException was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof RuntimeException) {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw (RuntimeException) e; // throw to make IDEA alert user
        } else {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw new RuntimeException(e); // rethrow as runtime to make IDEA alert user
        }
    }

  /**
   * Combines the two lists into one list of members.
   *
   * @param filteredFields  fields to be included in the dialog
   * @param filteredMethods methods to be included in the dialog
   * @return the combined list
   */
  public static PsiElementClassMember[] combineToClassMemberList(PsiField[] filteredFields, PsiMethod[] filteredMethods) {
      PsiElementClassMember[] members = new PsiElementClassMember[filteredFields.length + filteredMethods.length];

      // first add fields
      for (int i = 0; i < filteredFields.length; i++) {
          members[i] = new PsiFieldMember(filteredFields[i]);
      }

      // then add methods
      for (int i = 0; i < filteredMethods.length; i++) {
          members[filteredFields.length + i] = new PsiMethodMember(filteredMethods[i]);
      }

      return members;
  }

  /**
   * Converts the list of {@link com.intellij.codeInsight.generation.PsiElementClassMember} to {PsiMember} objects.
   *
   * @param classMemberList  list of {@link com.intellij.codeInsight.generation.PsiElementClassMember}
   * @return a list of {PsiMember} objects.
   */
  public static List<PsiMember> convertClassMembersToPsiMembers(@Nullable List<PsiElementClassMember> classMemberList) {
      if (classMemberList == null || classMemberList.isEmpty()) {
        return Collections.emptyList();
      }
      List<PsiMember> psiMemberList = new ArrayList<PsiMember>();

      for (PsiElementClassMember classMember : classMemberList) {
          psiMemberList.add(classMember.getElement());
      }

      return psiMemberList;
  }

  /**
   * Creates the <code>toString</code> method.
   * @param template        the template to use
   * @param params          additional parameters stored with key/value in the map.
   * @param policy          conflict resolution policy
   * @param selectedMembers the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
   * @return the created method, null if the method is not created due the user cancels this operation
   * @throws org.jetbrains.java.generate.exception.GenerateCodeException       is thrown when there is an error generating the javacode.
   * @throws com.intellij.util.IncorrectOperationException is thrown by IDEA.
   */
  @Nullable
  public static PsiMethod createMethodByTemplate(TemplateResource template,
                                                 PsiClass clazz,
                                                 Editor editor,
                                                 Collection<PsiMember> selectedMembers,
                                                 Map<String, String> params,
                                                 ConflictResolutionPolicy policy,
                                                 boolean hasOverrideAnnotation,
                                                 int sortElements,
                                                 boolean useFullyQualifiedName) throws IncorrectOperationException, GenerateCodeException {
    // generate code using velocity
    String body = velocityGenerateCode(clazz, selectedMembers, params, template.getMethodBody(), sortElements, useFullyQualifiedName);
    if (logger.isDebugEnabled()) logger.debug("Method body generated from Velocity:\n" + body);

    // fix weird linebreak problem in IDEA #3296 and later
    body = StringUtil.convertLineSeparators(body);

    // create psi newMethod named toString()
    final JVMElementFactory topLevelFactory = JVMElementFactories.getFactory(clazz.getLanguage(), clazz.getProject());
    if (topLevelFactory == null) {
      return null;
    }
    PsiMethod newMethod;
    try {
      newMethod = topLevelFactory.createMethodFromText(template.getMethodSignature() + " { " + body + " }", clazz);
      CodeStyleManager.getInstance(clazz.getProject()).reformat(newMethod);
    } catch (IncorrectOperationException ignore) {
      HintManager.getInstance().showErrorHint(editor, "'toString()' method could not be created from template '" +
                                                      template.getFileName() + '\'');
      return null;
    }

    // insertNewMethod conflict resolution policy (add/replace, duplicate, cancel)
    PsiMethod existingMethod = clazz.findMethodBySignature(newMethod, false);
    PsiMethod createdMethod = policy.applyMethod(clazz, existingMethod, newMethod, editor);
    if (createdMethod == null) {
      return null; // user cancelled so return null
    }

    if (hasOverrideAnnotation) {
      createdMethod.getModifierList().addAnnotation("java.lang.Override");
    }

    // applyJavaDoc conflict resolution policy (add or keep existing)
    String existingJavaDoc = params.get("existingJavaDoc");
    String newJavaDoc = template.getJavaDoc();
    if (existingJavaDoc != null || newJavaDoc != null) {
      // generate javadoc using velocity
      newJavaDoc = velocityGenerateCode(clazz, selectedMembers, params, newJavaDoc, sortElements, useFullyQualifiedName);
      if (logger.isDebugEnabled()) logger.debug("JavaDoc body generated from Velocity:\n" + newJavaDoc);

      applyJavaDoc(createdMethod, existingJavaDoc, newJavaDoc);
    }

    // return the created method
    return createdMethod;
  }

  private static void applyJavaDoc(PsiMethod newMethod, String existingJavaDoc, String newJavaDoc) {
    String text = newJavaDoc != null ? newJavaDoc : existingJavaDoc; // prefer to use new javadoc
    PsiAdapter.addOrReplaceJavadoc(newMethod, text, true);
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
   * @throws org.jetbrains.java.generate.exception.GenerateCodeException is thrown when there is an error generating the javacode.
   */
  private static String velocityGenerateCode(PsiClass clazz,
                                             Collection<PsiMember> selectedMembers,
                                             Map<String, String> params,
                                             String templateMacro,
                                             int sortElements,
                                             boolean useFullyQualifiedName)
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
      if (sortElements != 0) {
        Collections.sort(elements, new ElementComparator(sortElements));
      }
      vc.put("members", elements);

      // class information
      ClassElement ce = ElementFactory.newClassElement(clazz);
      vc.put("class", ce);
      if (logger.isDebugEnabled()) logger.debug("Velocity Context - adding class: " + ce);

      // information to keep as it is to avoid breaking compatibility with prior releases
      vc.put("classname", useFullyQualifiedName ? ce.getQualifiedName() : ce.getName());
      vc.put("FQClassname", ce.getQualifiedName());

      if (logger.isDebugEnabled()) logger.debug("Velocity Macro:\n" + templateMacro);

      // velocity
      VelocityEngine velocity = VelocityFactory.getVelocityEngine();
      logger.debug("Executing velocity +++ START +++");
      velocity.evaluate(vc, sw, GenerateToStringWorker.class.getName(), templateMacro);
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
}