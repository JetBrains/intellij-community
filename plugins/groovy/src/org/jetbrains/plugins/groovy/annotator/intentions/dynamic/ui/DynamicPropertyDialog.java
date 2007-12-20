package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicProperty;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.util.EventListener;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.12.2007
 */
public class DynamicPropertyDialog extends DialogWrapper {
  private JComboBox myContainingClassComboBox;
  private JPanel myDynPropDialogPanel;
  private JComboBox myTypeComboBox;
  private final DynamicPropertiesManager myDynamicPropertiesManager;
  private final Project myProject;
  private final DynamicProperty myDynamicProperty;
  private EventListenerList myListenerList = new EventListenerList();
  private final GrReferenceExpression myReferenceExpression;

  public DynamicPropertyDialog(Project project, DynamicProperty dynamicProperty, GrReferenceExpression referenceExpression) {
    super(project, true);
    myReferenceExpression = referenceExpression;
    setTitle(GroovyInspectionBundle.message("dynamic.property"));
    myProject = project;
    myDynamicProperty = dynamicProperty;
    myDynamicPropertiesManager = DynamicPropertiesManager.getInstance(project);

    init();

    setUpTypeComboBox();
    setUpContainingClassComboBox();

    myDynPropDialogPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void setUpContainingClassComboBox() {
    final PsiClass typeDefinition = myDynamicProperty.getContainingClass();
    assert typeDefinition != null;

    myContainingClassComboBox.addItem(new ContainingClassItem(typeDefinition));
    final Set<PsiClass> classes = GroovyUtils.findAllSupers(typeDefinition);
    for (PsiClass aClass : classes) {
      myContainingClassComboBox.addItem(new ContainingClassItem(aClass));
    }
  }


  private void setUpTypeComboBox() {
    //todo: implement my ComboboxEditor
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE);
//    final EditorComboBoxEditor comboEditor = DebuggerExpressionComboBox.


    myTypeComboBox.setEditor(comboEditor);
    myTypeComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));
    myTypeComboBox.setEditable(true);

//    myTypeComboBox.setMaximumRowCount();

    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myTypeComboBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            fireNameDataChanged();
          }
        }
    );

    ((EditorTextField) myTypeComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    final TypeItem item = new TypeItem(TypesUtil.createJavaLangObject(myReferenceExpression));
    myTypeComboBox.getEditor().setItem(item.getPresentableText());
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredTypeName();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

  @Nullable
  public String getEnteredTypeName() {
    final Object item = myTypeComboBox.getEditor().getItem();

//    if (item instanceof TypeItem) {
//      return ((TypeItem) item).getPresentableText();
//    } else {
//      return null;
//    }

    if (item instanceof String &&
        ((String) item).length() > 0) {
      return (String) item;
    } else {
      return null;
    }
  }

  public ContainingClassItem getEnteredContaningClass() {
    final Object item = myContainingClassComboBox.getSelectedItem();
    if (!(item instanceof ContainingClassItem)) return null;

    return ((ContainingClassItem) item);
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }


  @Nullable
  protected JComponent createCenterPanel() {
    return myDynPropDialogPanel;
  }

  protected void doOKAction() {
    myDynamicProperty.setType(getEnteredTypeName());
    myDynamicProperty.setContainingClass(getEnteredContaningClass().getContainingClass());
    myDynamicPropertiesManager.addDynamicProperty(myDynamicProperty);

    super.doOKAction();
  }

  class ContainingClassItem {
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

  class TypeItem {
    private final PsiType myPsiType;

    TypeItem(PsiType psiType) {
      myPsiType = psiType;
    }

    public String toString() {
      return myPsiType.getPresentableText();
    }

    @NotNull
    String getPresentableText() {
      return myPsiType.getPresentableText();
    }
  }
}
