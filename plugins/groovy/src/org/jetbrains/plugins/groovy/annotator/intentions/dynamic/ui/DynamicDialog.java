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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.12.2007
 */
public abstract class DynamicDialog extends DialogWrapper {
  private JComboBox myClassComboBox;
  private JPanel myPanel;
  private JComboBox myTypeComboBox;
  private JLabel myClassLabel;
  private JLabel myTypeLabel;
  private JPanel myTypeStatusPanel;
  private JLabel myTypeStatusLabel;
  private JTable myParametersTable;
  private JLabel myTableLabel;
  private JCheckBox myStaticCheckBox;
  private final DynamicManager myDynamicManager;
  private final Project myProject;
  private final EventListenerList myListenerList = new EventListenerList();
  private final GrReferenceExpression myReferenceExpression;
  private final DynamicElementSettings mySettings;

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog");

  public DynamicDialog(GrReferenceExpression referenceExpression) {
    super(referenceExpression.getProject(), true);
    myProject = referenceExpression.getProject();

    if (!isTableVisible()) {
      myParametersTable.setVisible(false);
      myTableLabel.setVisible(false);
    }
    myReferenceExpression = referenceExpression;
    setTitle(GroovyInspectionBundle.message("dynamic.element"));
    myDynamicManager = DynamicManager.getInstance(myProject);

    init();

    mySettings = QuickfixUtil.createSettings(myReferenceExpression);

    setUpTypeComboBox();
    setUpContainingClassComboBox();
    setUpStatusLabel();
    setUpStaticComboBox();

    myTableLabel.setLabelFor(myParametersTable);
    setUpTableNameLabel(GroovyBundle.message("dynamic.properties.table.name"));

    final Border border2 = BorderFactory.createLineBorder(Color.BLACK);
    myParametersTable.setBorder(border2);
    myParametersTable.setBackground(Color.WHITE);

    myTypeLabel.setLabelFor(myTypeComboBox);
    myClassLabel.setLabelFor(myClassComboBox);
  }

  private void setUpStaticComboBox() {
    myStaticCheckBox.setMnemonic(KeyEvent.VK_S);
    myStaticCheckBox.setSelected(mySettings.isStatic());
  }

  public DynamicElementSettings getSettings() {
    return mySettings;
  }

  protected void setUpTableNameLabel(String text) {
    myTableLabel.setText(text);
  }

  private void setUpStatusLabel() {
    if (!isTypeCheckerPanelEnable()) {
      myTypeStatusPanel.setVisible(false);
      return;
    }
    myTypeStatusLabel.setHorizontalTextPosition(SwingConstants.RIGHT);

    final GrTypeElement typeElement = getEnteredTypeName();
    if (typeElement == null) {
      setStatusTextAndIcon(IconLoader.getIcon("/compiler/warning.png"), GroovyInspectionBundle.message("no.type.specified"));
      return;
    }

    final PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) {
      setStatusTextAndIcon(IconLoader.getIcon("/compiler/warning.png"),
                           GroovyInspectionBundle.message("unresolved.type.status", type.getPresentableText()));
      return;
    }
    setStatusTextAndIcon(null, "");
  }

  private void setStatusTextAndIcon(final Icon icon, final String text) {
    myTypeStatusLabel.setIcon(icon);
    myTypeStatusLabel.setText(text);
    pack();
  }


  private void setUpContainingClassComboBox() {
    PsiClass targetClass = QuickfixUtil.findTargetClass(myReferenceExpression);

    if (targetClass == null || targetClass instanceof SyntheticElement) {
      try {
        final GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(myProject).createTypeElement("java.lang.Object");
        if (typeElement == null) return;

        final PsiType type = typeElement.getType();

        if (!(type instanceof PsiClassType)) LOG.error("Type java.lang.Object doesn't resolve");
        targetClass = ((PsiClassType) type).resolve();
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (targetClass == null) return;

    for (PsiClass aClass : PsiUtil.iterateSupers(targetClass, true)) {
      myClassComboBox.addItem(new ContainingClassItem(aClass));
    }

    myPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myClassComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  @Nullable
  private Document createDocument(final String text) {
    GroovyCodeFragment fragment = new GroovyCodeFragment(myProject, text);
    fragment.setContext(myReferenceExpression);
    return PsiDocumentManager.getInstance(myProject).getDocument(fragment);
  }

  private void setUpTypeComboBox() {
    final EditorComboBoxEditor comboEditor = new EditorComboBoxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE);

    final Document document = createDocument("");
    assert document != null;

    comboEditor.setItem(document);

    myTypeComboBox.setEditor(comboEditor);
    myTypeComboBox.setEditable(true);
    myTypeComboBox.grabFocus();

    addDataChangeListener();

    myTypeComboBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            fireDataChanged();
          }
        }
    );

    myPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);


    final EditorTextField editorTextField = (EditorTextField) myTypeComboBox.getEditor().getEditorComponent();

    editorTextField.addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireDataChanged();
      }
    });

    final TypeConstraint[] constrants = GroovyExpectedTypesUtil.calculateTypeConstraints(QuickfixUtil.isCall(myReferenceExpression) ? (GrExpression) myReferenceExpression.getParent() : myReferenceExpression);

    PsiType type = constrants.length == 1 ? constrants[0].getDefaultType() : TypesUtil.getJavaLangObject(myReferenceExpression);
    myTypeComboBox.getEditor().setItem(createDocument(type.getCanonicalText()));
  }

  protected void addDataChangeListener() {
    myListenerList.add(DataChangedListener.class, new DataChangedListener());
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  protected void updateOkStatus() {
    GrTypeElement typeElement = getEnteredTypeName();
    if (typeElement == null) {
      setOKActionEnabled(false);
    } else {
      setOKActionEnabled(true);
    }

    setUpStatusLabel();
  }

  @Nullable
  public GrTypeElement getEnteredTypeName() {
    final Document typeEditorDocument = getTypeEditorDocument();

    if (typeEditorDocument == null) return null;

    try {
      final String typeText = typeEditorDocument.getText();

      return GroovyPsiElementFactory.getInstance(myProject).createTypeElement(typeText);
    } catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nullable
  public Document getTypeEditorDocument() {
    final Object item = myTypeComboBox.getEditor().getItem();

    return item instanceof Document ? (Document) item : null;

  }

  @Nullable
  public ContainingClassItem getEnteredContainingClass() {
    final Object item = myClassComboBox.getSelectedItem();
    if (!(item instanceof ContainingClassItem)) return null;

    return ((ContainingClassItem) item);
  }

  protected void fireDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected void doOKAction() {
    super.doOKAction();

    mySettings.setContainingClassName(getEnteredContainingClass().getContainingClass().getQualifiedName());
    mySettings.setStatic(myStaticCheckBox.isSelected());
    GrTypeElement typeElement = getEnteredTypeName();

    if (typeElement == null) {
      mySettings.setType("java.lang.Object");
    } else {
      PsiType type = typeElement.getType();
      if (type instanceof PsiPrimitiveType) {
        type = TypesUtil.boxPrimitiveType(type, typeElement.getManager(), ProjectScope.getAllScope(myProject));
      }

      final String typeQualifiedName = type.getCanonicalText();

      if (typeQualifiedName != null) {
        mySettings.setType(typeQualifiedName);
      } else {
        mySettings.setType(type.getPresentableText());
      }
    }

    Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReferenceExpression.getContainingFile());
    final DocumentReference[] refs = new DocumentReference[]{DocumentReferenceManager.getInstance().create(document)};

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        UndoManager.getInstance(myProject).undoableActionPerformed(new UndoableAction() {
          public void undo() throws UnexpectedUndoException {

            final DItemElement itemElement;
            if (mySettings.isMethod()) {
              final List<MyPair> myPairList = mySettings.getPairs();
              final String[] argumentsTypes = QuickfixUtil.getArgumentsTypes(myPairList);
              itemElement = myDynamicManager.findConcreteDynamicMethod(mySettings.getContainingClassName(), mySettings.getName(), argumentsTypes);
            } else {
              itemElement = myDynamicManager.findConcreteDynamicProperty(mySettings.getContainingClassName(), mySettings.getName());
            }

            if (itemElement == null) {
              Messages.showWarningDialog(myProject, GroovyInspectionBundle.message("Cannot.perform.undo.operation"), GroovyInspectionBundle.message("Undo.disable"));
              return;
            }
            final DClassElement classElement = myDynamicManager.getClassElementByItem(itemElement);

            if (classElement == null) {
              Messages.showWarningDialog(myProject, GroovyInspectionBundle.message("Cannot.perform.undo.operation"), GroovyInspectionBundle.message("Undo.disable"));
              return;
            }

            removeElement(itemElement);

            if (classElement.getMethods().size() == 0 && classElement.getProperties().size() == 0) {
              myDynamicManager.removeClassElement(classElement);
            }
          }

          public void redo() throws UnexpectedUndoException {
            addElement(mySettings);
          }

          public DocumentReference[] getAffectedDocuments() {
            return refs;
          }

          public boolean isGlobal() {
            return true;
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
    } else {
      myDynamicManager.addProperty(settings);
    }

    myDynamicManager.fireChange();
  }

  static class ContainingClassItem {
    private final PsiClass myContainingClass;

    ContainingClassItem(PsiClass containingClass) {
      myContainingClass = containingClass;
    }

    public String toString() {
      return myContainingClass.getName();
    }

    public PsiClass getContainingClass() {
      return myContainingClass;
    }
  }

  public void doCancelAction() {
    super.doCancelAction();

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  public JComponent getPreferredFocusedComponent() {
    return myTypeComboBox;
  }

protected boolean isTableVisible() {
    return false;
  }

  public JTable getParametersTable() {
    return myParametersTable;
  }

  protected static boolean isTypeCheckerPanelEnable() {
    return true;
  }

  public Project getProject() {
    return myProject;
  }

  protected void setUpTypeLabel(String typeLabelText) {
    myTypeLabel.setText(typeLabelText);
  }
}
