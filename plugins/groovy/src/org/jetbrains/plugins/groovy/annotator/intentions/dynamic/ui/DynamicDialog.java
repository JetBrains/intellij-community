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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.12.2007
 */
public abstract class DynamicDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(DynamicDialog.class);

  private JComboBox myClassComboBox;
  private JPanel myPanel;
  private JComboBox myTypeComboBox;
  private JLabel myTypeLabel;
  protected JBTable myParametersTable;
  private JCheckBox myStaticCheckBox;
  private JPanel myTablePane;

  private final DynamicManager myDynamicManager;
  protected final Project myProject;
  private final PsiElement myContext;
  private final DynamicElementSettings mySettings;

  public DynamicDialog(PsiElement context, DynamicElementSettings settings, TypeConstraint[] typeConstraints, boolean isTableVisible) {
    super(context.getProject(), true);
    myProject = context.getProject();
    mySettings = settings;
    myContext = context;
    myDynamicManager = DynamicManager.getInstance(myProject);

    if (isTableVisible) {
      myTablePane.setBorder(IdeBorderFactory.createTitledBorder(GroovyBundle.message("dynamic.properties.table.name"), false));
    }
    else {
      myTablePane.setVisible(false);
    }

    setTitle(GroovyInspectionBundle.message("dynamic.element"));
    setUpTypeComboBox(typeConstraints);
    setUpContainingClassComboBox();
    setUpStaticComboBox();

    init();
  }

  private void setUpStaticComboBox() {
    myStaticCheckBox.setSelected(mySettings.isStatic());
  }

  public DynamicElementSettings getSettings() {
    return mySettings;
  }

  @Override
  protected ValidationInfo doValidate() {
    final GrTypeElement typeElement = getEnteredTypeName();
    if (typeElement == null) {
      return new ValidationInfo(GroovyInspectionBundle.message("no.type.specified"), myTypeComboBox);
    }

    final PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) {
      return new ValidationInfo(GroovyInspectionBundle.message("unresolved.type.status", type.getPresentableText()), myTypeComboBox);
    }
    return null;
  }

  private void setUpContainingClassComboBox() {
    String containingClassName = mySettings.getContainingClassName();
    PsiClass targetClass = JavaPsiFacade.getInstance(myProject).findClass(containingClassName, GlobalSearchScope.allScope(myProject));
    if (targetClass == null || targetClass instanceof SyntheticElement) {
      if (!containingClassName.isEmpty()) {
        myClassComboBox.addItem(containingClassName);
      }

      if (!containingClassName.equals(CommonClassNames.JAVA_LANG_OBJECT)) {
        myClassComboBox.addItem(CommonClassNames.JAVA_LANG_OBJECT);
      }

      return;
    }

    for (PsiClass aClass : PsiUtil.iterateSupers(targetClass, true)) {
      myClassComboBox.addItem(aClass.getQualifiedName());
    }
  }

  @Nullable
  private Document createDocument(final String text) {
    GroovyCodeFragment fragment = new GroovyCodeFragment(myProject, text);
    fragment.setContext(myContext);
    return PsiDocumentManager.getInstance(myProject).getDocument(fragment);
  }

  private void setUpTypeComboBox(TypeConstraint[] typeConstraints) {
    final EditorComboBoxEditor comboEditor = new EditorComboBoxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE);

    final Document document = createDocument("");
    LOG.assertTrue(document != null);

    comboEditor.setItem(document);

    myTypeComboBox.setEditor(comboEditor);
    myTypeComboBox.setEditable(true);
    myTypeComboBox.grabFocus();

    PsiType type = typeConstraints.length == 1 ? typeConstraints[0].getDefaultType() : TypesUtil.getJavaLangObject(myContext);
    myTypeComboBox.getEditor().setItem(createDocument(type.getCanonicalText()));
  }

  @Nullable
  public GrTypeElement getEnteredTypeName() {
    final Document typeEditorDocument = getTypeEditorDocument();

    if (typeEditorDocument == null) return null;
    try {
      return GroovyPsiElementFactory.getInstance(myProject).createTypeElement(typeEditorDocument.getText());
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nullable
  public Document getTypeEditorDocument() {
    final Object item = myTypeComboBox.getEditor().getItem();

    return item instanceof Document ? (Document)item : null;
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    mySettings.setContainingClassName((String)myClassComboBox.getSelectedItem());
    mySettings.setStatic(myStaticCheckBox.isSelected());
    GrTypeElement typeElement = getEnteredTypeName();

    if (typeElement == null) {
      mySettings.setType(CommonClassNames.JAVA_LANG_OBJECT);
    }
    else {
      PsiType type = typeElement.getType();
      if (type instanceof PsiPrimitiveType) {
        type = TypesUtil.boxPrimitiveType(type, typeElement.getManager(), ProjectScope.getAllScope(myProject));
      }

      final String typeQualifiedName = type.getCanonicalText();

      if (typeQualifiedName != null) {
        mySettings.setType(typeQualifiedName);
      }
      else {
        mySettings.setType(type.getPresentableText());
      }
    }

    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myContext.getContainingFile());

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        UndoManager.getInstance(myProject).undoableActionPerformed(new GlobalUndoableAction(document) {
          @Override
          public void undo() throws UnexpectedUndoException {

            final DItemElement itemElement;
            if (mySettings.isMethod()) {
              final List<ParamInfo> myPairList = mySettings.getParams();
              final String[] argumentsTypes = QuickfixUtil.getArgumentsTypes(myPairList);
              itemElement =
                myDynamicManager.findConcreteDynamicMethod(mySettings.getContainingClassName(), mySettings.getName(), argumentsTypes);
            }
            else {
              itemElement = myDynamicManager.findConcreteDynamicProperty(mySettings.getContainingClassName(), mySettings.getName());
            }

            if (itemElement == null) {
              Messages.showWarningDialog(myProject, GroovyInspectionBundle.message("Cannot.perform.undo.operation"),
                                         GroovyInspectionBundle.message("Undo.disable"));
              return;
            }
            final DClassElement classElement = myDynamicManager.getClassElementByItem(itemElement);

            if (classElement == null) {
              Messages.showWarningDialog(myProject, GroovyInspectionBundle.message("Cannot.perform.undo.operation"),
                                         GroovyInspectionBundle.message("Undo.disable"));
              return;
            }

            removeElement(itemElement);

            if (classElement.getMethods().isEmpty() && classElement.getProperties().isEmpty()) {
              myDynamicManager.removeClassElement(classElement);
            }
          }

          @Override
          public void redo() throws UnexpectedUndoException {
            addElement(mySettings);
          }
        });

        addElement(mySettings);
      }
    }, "Add dynamic element", null);
  }

  private void removeElement(DItemElement itemElement) {
    myDynamicManager.removeItemElement(itemElement);
    myDynamicManager.fireChange();
  }

  public void addElement(final DynamicElementSettings settings) {
    if (settings.isMethod()) {
      myDynamicManager.addMethod(settings);
    }
    else {
      myDynamicManager.addProperty(settings);
    }

    myDynamicManager.fireChange();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTypeComboBox;
  }

  protected void setUpTypeLabel(String typeLabelText) {
    myTypeLabel.setText(typeLabelText);
  }
}
