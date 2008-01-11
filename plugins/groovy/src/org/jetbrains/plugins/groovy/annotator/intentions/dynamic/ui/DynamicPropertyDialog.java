package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.real.DynamicPropertyReal;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.awt.*;
import java.util.EventListener;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.12.2007
 */
public class DynamicPropertyDialog extends DialogWrapper {
  private JComboBox myContainingClassComboBox;
  private JPanel myDynamicPropertyDialogPanel;
  private JComboBox myTypeComboBox;
  //  private JComboBox myTypeComboBox;
  private final DynamicPropertiesManager myDynamicPropertiesManager;
  private final Project myProject;
  private final DynamicPropertyReal myDynamicPropertyReal;
  private EventListenerList myListenerList = new EventListenerList();
  private final GrReferenceExpression myReferenceExpression;

  public DynamicPropertyDialog(Project project, DynamicPropertyReal dynamicPropertyReal, GrReferenceExpression referenceExpression) {
    super(project, true);
    myReferenceExpression = referenceExpression;
    setTitle(GroovyInspectionBundle.message("dynamic.property"));
    myProject = project;
    myDynamicPropertyReal = dynamicPropertyReal;
    myDynamicPropertiesManager = DynamicPropertiesManager.getInstance(project);

    init();

    setUpTypeComboBox();
    setUpContainingClassComboBox();

    myDynamicPropertyDialogPanel.addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        myTypeComboBox.setCursor(Cursor.getDefaultCursor());
      }
    });

    myDynamicPropertyDialogPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void setUpContainingClassComboBox() {
    final PsiClass typeDefinition = myDynamicPropertyReal.getContainingClass();
    assert typeDefinition != null;

    myContainingClassComboBox.addItem(new ContainingClassItem(typeDefinition));
    final Set<PsiClass> classes = GroovyUtils.findAllSupers(typeDefinition);
    for (PsiClass aClass : classes) {
      myContainingClassComboBox.addItem(new ContainingClassItem(aClass));
    }
  }

  private Document createDocument(final String text) {
    final PsiFile groovyFile = GroovyElementFactory.getInstance(myProject).createGroovyFile(text, true);
    return PsiDocumentManager.getInstance(myReferenceExpression.getProject()).getDocument(groovyFile);
  }

  private void setUpTypeComboBox() {
    final EditorComboBoxEditor comboEditor = new EditorComboBoxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE);

    final Document document = createDocument("");
    comboEditor.setItem(document);

    myTypeComboBox.setEditor(comboEditor);
    myTypeComboBox.setEditable(true);
    myTypeComboBox.grabFocus();

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

    final PsiClassType objectType = TypesUtil.createJavaLangObject(myReferenceExpression);
    myTypeComboBox.getEditor().setItem(createDocument(objectType.getCanonicalText()));
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    //TODO
    String text = getEnteredTypeName();
    setOKActionEnabled(/*GroovyNamesUtil.isIdentifier(text)*/true);
  }

  @Nullable
  public String getEnteredTypeName() {
    final Object item = myTypeComboBox.getEditor().getItem();

    if (item instanceof Document) {
      final Document document = (Document) item;
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (psiFile instanceof GroovyFile) {
        final GrTopStatement[] topStatements = ((GroovyFile) psiFile).getTopStatements();

        if (topStatements.length != 1) return null;
        final GrTopStatement qualifierTypeName = topStatements[0];

        if (!(qualifierTypeName instanceof GrReferenceExpression)) return null;
        final GrReferenceExpression typeReferenceExpression = (GrReferenceExpression) qualifierTypeName;

//        final PsiElement resolvedType = typeReferenceExpression.resolve();
//
//        if (resolvedType == null) return typeReferenceExpression.getName();
//        if (resolvedType instanceof PsiClassType) return ((PsiClassType) resolvedType).getCanonicalText();
//
//        return typeReferenceExpression.getName();
        return typeReferenceExpression.getCanonicalText();
      }

      return document.getText();
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
    return myDynamicPropertyDialogPanel;
  }

  protected void doOKAction() {
    myDynamicPropertyReal.setType(getEnteredTypeName());
    myDynamicPropertyReal.setContainingClass(getEnteredContaningClass().getContainingClass());
    myDynamicPropertiesManager.addDynamicProperty(new DynamicPropertyVirtual(myDynamicPropertyReal.getPropertyName(), myDynamicPropertyReal.getContainingClassQualifiedName(), myDynamicPropertyReal.getModuleName(), myDynamicPropertyReal.getTypeName()));

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
