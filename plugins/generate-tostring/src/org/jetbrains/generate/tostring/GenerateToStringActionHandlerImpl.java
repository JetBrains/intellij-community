/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.generate.tostring.config.Config;
import org.jetbrains.generate.tostring.config.ConflictResolutionPolicy;
import org.jetbrains.generate.tostring.config.DuplicatePolicy;
import org.jetbrains.generate.tostring.config.InsertNewMethodPolicy;
import org.jetbrains.generate.tostring.element.*;
import org.jetbrains.generate.tostring.exception.GenerateCodeException;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.util.StringUtil;
import org.jetbrains.generate.tostring.velocity.VelocityFactory;
import org.jetbrains.generate.tostring.view.MethodExistsDialog;
import org.jetbrains.generate.tostring.view.TemplateSelectionActionDialog;

import javax.swing.*;
import java.io.StringWriter;
import java.util.*;

/**
 * The action-handler that does the code generation.
 */
public class GenerateToStringActionHandlerImpl extends EditorWriteActionHandler implements GenerateToStringActionHandler {
    private static final Logger logger = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringActionHandlerImpl");

    private PsiAdapter psi;
    private Config config;
    private Project project;
    private PsiManager manager;
    private PsiElementFactory elementFactory;
    private CodeStyleManager codeStyleManager;
    private Editor editor;
    private PsiJavaFile javaFile;
    private PsiClass clazz;

    public void executeWriteAction(Editor editor, DataContext dataContext) {
        this.psi = PsiAdapterFactory.getPsiAdapter();
        this.project = editor.getProject();
        PsiJavaFile javaFile = psi.getSelectedJavaFile(project, psi.getPsiManager(project));
        PsiClass clazz = psi.getCurrentClass(javaFile, editor);

        doExecuteAction(project, clazz, null, null);
    }

    public void executeActionTemplateQuickSelection(final Project project, final PsiClass clazz, final TemplateResource quickTemplate, final InsertNewMethodPolicy insertPolicy) {
        if (quickTemplate == null) {
            throw new IllegalArgumentException("No quick selection template selected");
        }

        doExecuteAction(project, clazz, quickTemplate, insertPolicy);
    }


    public void executeActionQickFix(final Project project, final PsiClass clazz, final ProblemDescriptor desc, final InsertNewMethodPolicy insertPolicy) {
        doExecuteAction(project, clazz, null, insertPolicy);
    }

    /**
     * Entry for performing the action and code generation.
     *
     * @param project         the project, must not be <tt>null<tt>
     * @param clazz           the class, must not be <tt>null<tt>
     * @param quickTemplate   use this quick template, if <tt>null</tt> then the default template is used
     * @param insertPolicy    overrule to use this policy (usually by quickfix), <tt>null</tt> to use default
     */
    private void doExecuteAction(@NotNull final Project project, @NotNull final PsiClass clazz, final TemplateResource quickTemplate, final InsertNewMethodPolicy insertPolicy) {
        logger.debug("+++ doExecuteAction - START +++");

        if (logger.isDebugEnabled()) {
            logger.debug("Current project " + project.getName());
        }

        // set all instance variabels
        this.project = project;
        this.clazz = clazz;
        this.psi = PsiAdapterFactory.getPsiAdapter();
        this.javaFile = psi.getSelectedJavaFile(project, psi.getPsiManager(project));
        this.config = GenerateToStringContext.getConfig();
        this.editor = psi.getSelectedEditor(project);
        this.manager = psi.getPsiManager(project);
        this.elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        this.codeStyleManager = psi.getCodeStyleManager(project);

        if (quickTemplate == null && config.isEnableTemplateQuickList()) {
            // display quick template dialog
            if (displayQuickTemplateDialog(insertPolicy)) {
                return; // return as the qucik template will execute this action again with the selected body to use
            }
        }

        // should we use quick template or default active template
        final TemplateResource template = (quickTemplate == null ? GenerateToStringContext.getConfig().getActiveTemplate() : quickTemplate);

        // is it a valid template
        if (!template.isValidTemplate()) {
            Messages.showWarningDialog("The template is incompatible with this version of the plugin. See the default templates for compatible samples.", "Incompatible Template");
            return;
        }

        try {
            PsiField[] filteredFields = GenerateToStringUtils.filterAvailableFields(project, psi, clazz, config.getFilterPattern());
            if (logger.isDebugEnabled()) logger.debug("Number of fields after filtering: " + filteredFields.length);

            PsiMethod[] filteredMethods = new PsiMethod[0];
            if (config.enableMethods) {
                // filter methods as it is enabled from config
                filteredMethods = GenerateToStringUtils.filterAvailableMethods(psi, clazz, config.getFilterPattern());
                if (logger.isDebugEnabled()) logger.debug("Number of methods after filtering: " + filteredMethods.length);
            }

            if (displayMememberChooser(filteredFields.length, filteredMethods.length, template)) {
                logger.debug("Displaying member chooser dialog");
                PsiElementClassMember[] dialogMembers = GenerateToStringUtils.combineToClassMemberList(filteredFields, filteredMethods);
                final MemberChooser dialog = new MemberChooser(dialogMembers, true, true, project, false);
                // 1nd boolean is ???
                // 2nd boolean is to preselect all members or none
                // last boolean is to show/hide Insert @Override option in dialog
                dialog.setCopyJavadocVisible(false);
                dialog.selectElements(dialogMembers);
                dialog.setTitle("Choose Members for " + template.getTargetMethodName());
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        dialog.show();
                        if (MemberChooser.CANCEL_EXIT_CODE == dialog.getExitCode()) {
                            return;  // stop action, since user clicked cancel in dialog
                        }
                        Collection<PsiMember> selectedMembers = GenerateToStringUtils.convertClassMembersToPsiMembers(dialog.getSelectedElements());
                        executeGenerateActionLater(selectedMembers, template, insertPolicy);
                    }
                });
            } else {
                // no dialog, so select all fields (filtered) and methods (filtered)
                logger.debug("Member chooser dialog not used - either disabled in settings or no fields/methods to select after filtering");

                Collection<PsiMember> selectedMembers = Arrays.asList(GenerateToStringUtils.combineToMemberList(filteredFields, filteredMethods));
                executeGenerateAction(selectedMembers, template, insertPolicy);
            }
        } catch (IncorrectOperationException e) {
            GenerateToStringUtils.handleExeption(project, e);
        } catch (GenerateCodeException e) {
            GenerateToStringUtils.handleExeption(project, e);
        }

        logger.debug("+++ doExecuteAction - END +++");
    }


    /**
     * Display the quick template selection dialog.
     *
     * @param insertPolicy  the policy choosen
     * @return  true if a template was selected and thus would be handled by this action, false if no quick templates to select.
     */
    private boolean displayQuickTemplateDialog(InsertNewMethodPolicy insertPolicy) {
        String selected = config.getSelectedQuickTemplates();
        if (selected == null) {
            return false;
        }

        // convert semi colon based String to List of Strings
        List<String> options = new ArrayList<String>();
        String[] text = selected.split(";");
        options.addAll(Arrays.asList(text));

        TemplateSelectionActionDialog dialog = new TemplateSelectionActionDialog(project, clazz, options, "Select Template to use", insertPolicy);
        dialog.show();
        return true;
    }

    /**
     * Should the memeber chooser dialog be shown to the user?
     *
     * @param numberOfFields    number of fields to be avail for selection
     * @param numberOfMethods   number of methods to be avail for selection
     * @param template          the template to use
     * @return true if the dialog should be shown, false if not.
     */
    private boolean displayMememberChooser(int numberOfFields, int numberOfMethods, TemplateResource template) {
        // do not show if disabled in settings
        if (! config.isUseFieldChooserDialog()) {
            return false;
        }

        // if using reflection in toString() body code then do not display dialog
        if (template.getMethodBody() != null && template.getMethodBody().indexOf("getDeclaredFields()") != -1) {
            return false;
        }

        // must be at least one field for selection
        if (! config.enableMethods && numberOfFields == 0) {
            return false;
        }

        // must be at least one field or method for selection
        if (config.enableMethods && Math.max(numberOfFields, numberOfMethods) == 0) {
            return false;
        }

        return true;
    }

    /**
     * Generates the toString() code for the specified class and selected fields and methods.
     *
     * @param selectedMembers   the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
     * @param template          the template to use
     * @param insertPolicy      overrule to use this policy (usually by quickfix), null to use default
     * @throws IncorrectOperationException   is thrown by IDEA
     * @throws GenerateCodeException         is thrown if the code could not be generated
     */
    private void executeGenerateAction(Collection<PsiMember> selectedMembers, TemplateResource template, InsertNewMethodPolicy insertPolicy) throws IncorrectOperationException, GenerateCodeException {

        // decide what to do if the method already exists
        ConflictResolutionPolicy policy = exitsMethodDialog(template);
        // what insert policy should we use?
        policy.setInsertNewMethodPolicy(insertPolicy != null ? insertPolicy : config.getInsertNewMethodInitialOption());

        // user didn't click cancel so go on
        Map<String, String> params = new HashMap<String, String>();

        // before
        beforeCreateToStringMethod(selectedMembers, params, template);

        // generate method
        PsiMethod method = createToStringMethod(selectedMembers, policy, params, template);

        // after, if method was generated (not cancel policy)
        if (method != null)
            afterCreateToStringMethod(method, policy, params, template);
    }

    /**
     * Generates the toString() code for the specified class and selected
     * fields, doing the work through a WriteAction ran by a CommandProcessor.
     *
     * @param selectedMemebers  list of members selected
     * @param template          the choosen template to use
     * @param insertPolicy      overrule to use this policy (usually by quickfix), null to use default
     */
    private void executeGenerateActionLater(final Collection<PsiMember> selectedMemebers, final TemplateResource template, final InsertNewMethodPolicy insertPolicy) {
        Runnable writeCommand = new Runnable() {
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    public void run() {
                        try {
                            executeGenerateAction(selectedMemebers, template, insertPolicy);
                        } catch (Exception e) {
                            GenerateToStringUtils.handleExeption(project, e);
                        }
                    }
                });
            }
        };

        psi.executeCommand(project, writeCommand);
    }

    /**
     * This method get's the choice if there is an existing <code>toString</code> method.
     * <br/> 1) If there is a settings to always override use this.
     * <br/> 2) Prompt a dialog and let the user decide.
     *
     * @param template          the choosen template to use
     * @return the policy the user selected (never null)
     */
    private ConflictResolutionPolicy exitsMethodDialog(TemplateResource template) {
        PsiMethod existingMethod = psi.findMethodByName(clazz, template.getTargetMethodName());
        if (existingMethod != null) {
            ConflictResolutionPolicy def = config.getReplaceDialogInitialOption();
            // is always use default set in config?
            if (config.isUseDefaultAlways()) {
                return def;
            } else {
                // no, so ask user what to do
                return MethodExistsDialog.showDialog(template.getTargetMethodName());
            }
        }

        // If there is no conflict, duplicate policy will do the trick
        return DuplicatePolicy.getInstance();
    }

    /**
     * This method is executed just before the <code>toString</code> method is created or updated.
     *
     * @param selectedMembers   the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
     * @param params            additional parameters stored with key/value in the map.
     * @param template          the template to use
     */
    private void beforeCreateToStringMethod(Collection<PsiMember> selectedMembers, Map<String, String> params, TemplateResource template) {
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
     * @param selectedMembers   the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
     * @param policy            conflict resolution policy
     * @param params            additional parameters stored with key/value in the map.
     * @param template          the template to use
     * @return the created method, null if the method is not created due the user cancels this operation
     * @throws GenerateCodeException is thrown when there is an error generating the javacode.
     * @throws IncorrectOperationException is thrown by IDEA.
     */
    private PsiMethod createToStringMethod(Collection<PsiMember> selectedMembers, ConflictResolutionPolicy policy, Map<String, String> params, TemplateResource template) throws IncorrectOperationException, GenerateCodeException {
        // generate code using velocity
        String body = velocityGenerateCode(selectedMembers, params, template, template.getMethodBody());
        if (logger.isDebugEnabled()) logger.debug("Method body generated from Velocity:\n" + body);

        // fix weird linebreak problem in IDEA #3296 and later
        body = StringUtil.fixLineBreaks(body);

        // create psi newMethod named toString()
        PsiMethod newMethod = elementFactory.createMethodFromText(template.getMethodSignature() + " { " + body + " }", null);
        codeStyleManager.reformat(newMethod);

        // insertNewMethod conflict resolution policy (add/replace, duplicate, cancel)
        PsiMethod existingMethod = psi.findMethodByName(clazz, template.getTargetMethodName());
        boolean operationExectued = policy.applyMethod(clazz, existingMethod, newMethod);
        if (! operationExectued)
            return null; // user cancelled so return null

        // add annotations
        if (template.hasAnnotations()) {
            PsiMethod toStringMethod = psi.findMethodByName(clazz, template.getTargetMethodName()); // must find again to be able to add javadoc (IDEA does not add if using method parameter)
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
            PsiMethod toStringMethod = psi.findMethodByName(clazz, template.getTargetMethodName()); // must find again to be able to add javadoc (IDEA does not add if using method parameter)

            // generate javadoc using velocity
            newJavaDoc = velocityGenerateCode(selectedMembers, params, template, newJavaDoc);
            if (logger.isDebugEnabled()) logger.debug("JavaDoc body generated from Velocity:\n" + newJavaDoc);

            policy.applyJavaDoc(clazz, toStringMethod, elementFactory, codeStyleManager, existingJavaDoc, newJavaDoc);
        }

        // reformat code style
        codeStyleManager.reformat(newMethod);

        // return the created method
        return newMethod;
    }

    /**
     * This method is executed just after the <code>toString</code> method is created or updated.
     *
     * @param method            the newly created/updated <code>toString</code> method.
     * @param policy            the policy selected
     * @param params            additional parameters stored with key/value in the map.
     * @param template          the template to use
     * @throws IncorrectOperationException  is thrown by IDEA
     */
    private void afterCreateToStringMethod(PsiMethod method, ConflictResolutionPolicy policy, Map<String, String> params, TemplateResource template) throws IncorrectOperationException {

        // if the code uses Arrays, then make sure java.util.Arrays is imported.
        String javaCode = method.getText();
        if (javaCode.indexOf("Arrays.") > 0 && !(psi.hasImportStatement(javaFile, "java.util.*") || psi.hasImportStatement(javaFile, "java.util.Arrays"))) {
            // java.util.Arrays must be imported as java.util.* since the addImportStatement method doens't support onDemand-import statement yet.
            psi.addImportStatement(javaFile, "java.util.*", elementFactory);
        }

        // if the code uses Reflection (Field[]), then make sure java.lang.reflect.Field is imported.
        if (javaCode.indexOf("Field[]") > 0 && !(psi.hasImportStatement(javaFile, "java.lang.reflect.*") || psi.hasImportStatement(javaFile, "java.lang.reflect.Field"))) {
            // java.lang.reflect.Field must be imported as java.lang.reflect.* since the addImportStatement method doens't support onDemand-import statement yet.
            psi.addImportStatement(javaFile, "java.lang.reflect.*", elementFactory);
        }

        // perform automatic import of packages if enabled in configuration
        if (config.isAutoImports()) {
            autoImportPackages(config.getAutoImportsPackages());
        }

        // any additional packages to import from the params
        if (params.get("autoImportPackages") != null) {
            autoImportPackages(params.get("autoImportPackages"));
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
                    if (logger.isDebugEnabled()) logger.debug("Moving/Scrolling caret to " + vp +  " (offset=" + offset + ")");
                    editor.getCaretModel().moveToVisualPosition(vp);
                    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_DOWN);
                }
            }
        }
    }

    /**
     * Automatic import the packages.
     *
     * @param packageNames   names of packages (must end with .* and be seperated by ; or ,)
     * @throws IncorrectOperationException   error adding imported package
     */
    private void autoImportPackages(String packageNames) throws IncorrectOperationException {
        StringTokenizer tok = new StringTokenizer(packageNames, ",");
        while (tok.hasMoreTokens()) {
            String packageName = tok.nextToken().trim(); // trim in case of space
            if (logger.isDebugEnabled()) logger.debug("Auto importing package: " + packageName);
            psi.addImportStatement(javaFile, packageName, elementFactory);
        }
    }

    /**
     * Generates the code using Velocity.
     * <p/>
     * This is used to create the <code>toString</code> method body and it's javadoc.
     *
     * @param selectedMembers  the selected members as both {@link com.intellij.psi.PsiField} and {@link com.intellij.psi.PsiMethod}.
     * @param params           additional parameters stored with key/value in the map.
     * @param template         overriding template to use (if using quick template selection dialog), can be null.
     * @param templateMacro    the veloicty macro template
     * @return code (usually javacode). Returns null if templateMacro is null.
     * @throws GenerateCodeException is thrown when there is an error generating the javacode.
     */
    private String velocityGenerateCode(Collection<PsiMember> selectedMembers, Map<String, String> params, TemplateResource template, String templateMacro) throws GenerateCodeException {
        if (templateMacro == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        try {
            VelocityContext vc = new VelocityContext();

            // field information
            logger.debug("Velocity Context - adding fields");
            vc.put("fields", ElementUtils.getOnlyAsFieldElements(project, elementFactory, psi, selectedMembers));

            // method information
            logger.debug("Velocity Context - adding methods");
            vc.put("methods", ElementUtils.getOnlyAsMethodElements(elementFactory, psi, selectedMembers));

            // element information (both fields and methods)
            logger.debug("Velocity Context - adding members (fields and methods)");
            List<Element> elements = ElementUtils.getOnlyAsFieldAndMethodElements(project, elementFactory, psi, selectedMembers);
            // sort elements if enabled and not using chooser dialog
            if (config.getSortElements() != 0 && !config.isUseFieldChooserDialog()) {
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
                params.put("autoImportPackages", (String) vc.get("autoImportPackages"));
            }

            // add java.io.Serializable if choosen in [settings] and does not already implements it
            if (config.isAddImplementSerializable() && !ce.isImplements("java.io.Serializable")) {
                psi.addImplements(project, clazz, "java.io.Serializable");
            }

        } catch (Exception e) {
            throw new GenerateCodeException("Error in Velocity code generator", e);
        }

        return sw.getBuffer().toString();
    }


}
