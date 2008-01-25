package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.real.DynamicProperty;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
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
  private JComboBox myClassComboBox;
  private JPanel myPanel;
  private JComboBox myTypeComboBox;
  private JLabel myClassLabel;
  private JLabel myTypeLabel;
  //  private JComboBox myTypeComboBox;
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

//    myPanel.addHierarchyListener(new HierarchyListener() {
//      public void hierarchyChanged(HierarchyEvent e) {
//        myTypeComboBox.setCursor(Cursor.getDefaultCursor());
//      }
//    });

    myTypeLabel.setLabelFor(myTypeComboBox);
    myClassLabel.setLabelFor(myClassComboBox);
    
  }



  private void setUpContainingClassComboBox() {
    final PsiClass typeDefinition = myDynamicProperty.getContainingClass();
    assert typeDefinition != null;

    myClassComboBox.addItem(new ContainingClassItem(typeDefinition));
    final Set<PsiClass> classes = GroovyUtils.findAllSupers(typeDefinition);
    for (PsiClass aClass : classes) {
      myClassComboBox.addItem(new ContainingClassItem(aClass));
    }

    myPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myClassComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private Document createDocument(final String text) {
    final PsiFile groovyFile = GroovyPsiElementFactory.getInstance(myProject).createGroovyFile(text, true, null);
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

    myPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);


    ((EditorTextField) myTypeComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    final PsiClassType objectType = TypesUtil.createJavaLangObject(myReferenceExpression);
    myTypeComboBox.getEditor().setItem(createDocument(objectType.getPresentableText()));
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    GrTypeElement typeElement = getEnteredTypeName();

    if (typeElement == null) {
      setOKActionEnabled(false);

    } else {
//      PsiType type = typeElement.getType();
//      if (type instanceof PsiClassType) {
//        setOKActionEnabled(((PsiClassType) type).resolve() != null);
//
//      } else if (type instanceof PsiPrimitiveType) {
//        setOKActionEnabled(true);
//      }
      setOKActionEnabled(true);
    }
  }

  @Nullable
  public GrTypeElement getEnteredTypeName() {
    final Object item = myTypeComboBox.getEditor().getItem();

    if (item instanceof Document) {
      final Document document = (Document) item;
      String documentText = document.getText();

      if (!documentText.matches("[a-zA-Z(.)]+")) return null;
      try {
        return GroovyPsiElementFactory.getInstance(myProject).createTypeElement(documentText);
      } catch (IncorrectOperationException e) {
        return null;
      }
    }
    return null;
  }

  public ContainingClassItem getEnteredContaningClass() {
    final Object item = myClassComboBox.getSelectedItem();
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
    return myPanel;
  }

  protected void doOKAction() {
    GrTypeElement typeElement = getEnteredTypeName();

    if (typeElement == null) {
      myDynamicProperty.setType("java.lang.Object");
    } else {
      PsiType type = typeElement.getType();
      if (type instanceof PsiPrimitiveType) {
        type = TypesUtil.boxPrimitiveType(type, typeElement.getManager(), myProject.getAllScope());
      }

      myDynamicProperty.setType(type.getCanonicalText());
    }
    myDynamicProperty.setContainingClass(getEnteredContaningClass().getContainingClass());
    myDynamicPropertiesManager.addDynamicProperty(new DynamicPropertyVirtual(myDynamicProperty.getPropertyName(), myDynamicProperty.getContainingClassQualifiedName(), myDynamicProperty.getModuleName(), myDynamicProperty.getTypeName()));

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

  public void doCancelAction() {
    super.doCancelAction();

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  public JComponent getPreferredFocusedComponent() {
    return myTypeComboBox;
  }
}
