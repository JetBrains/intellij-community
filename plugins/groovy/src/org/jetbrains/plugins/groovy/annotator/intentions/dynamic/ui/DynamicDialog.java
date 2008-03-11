package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;

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
  private JTable myTable;
  private JLabel myTableLabel;
  private final DynamicManager myDynamicManager;
  private final Project myProject;
  private final DItemElement myDynamicElement;
  private EventListenerList myListenerList = new EventListenerList();
  private final GrReferenceExpression myReferenceExpression;
  private final Module myModule;

  public DynamicDialog(Module module, DItemElement virtualElement, GrReferenceExpression referenceExpression) {
    super(module.getProject(), true);
    myProject = module.getProject();
    myModule = module;

    if (!isTableVisible()) {
      myTable.setVisible(false);
      myTableLabel.setVisible(false);
    }
    myReferenceExpression = referenceExpression;
    setTitle(GroovyInspectionBundle.message("dynamic.element"));

    myDynamicElement = virtualElement;
    myDynamicManager = DynamicManager.getInstance(myProject);

    init();

    setUpTypeComboBox();
    setUpContainingClassComboBox();
    setUpStatusLabel();
    setUpTableNameLabel();

    final Border border2 = BorderFactory.createLineBorder(Color.BLACK);
    myTable.setBorder(border2);

    myTypeLabel.setLabelFor(myTypeComboBox);
    myClassLabel.setLabelFor(myClassComboBox);
  }

  private void setUpTableNameLabel() {
    myTableLabel.setLabelFor(myTable);
    myTableLabel.setText(GroovyBundle.message("dynamic.properties.table.name"));
  }

  private void setUpStatusLabel() {
    if (!isTypeChekerPanelEnable()) {
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
    setStatusTextAndIcon(IconLoader.getIcon("/compiler/information.png"), GroovyInspectionBundle.message("resolved.type.status", type.getPresentableText()));
  }

  private void setStatusTextAndIcon(final Icon icon, final String text) {
    myTypeStatusLabel.setIcon(icon);
    myTypeStatusLabel.setText(text);
  }


  private void setUpContainingClassComboBox() {
    final String typeDefinition;

    final PsiClass targetClass = QuickfixUtil.findTargetClass(myReferenceExpression);

    if (targetClass == null) typeDefinition = "java.lang.Object";
    else typeDefinition = targetClass.getQualifiedName();

    final PsiClassType type = TypesUtil.createType(typeDefinition, myReferenceExpression);
    final PsiClass psiClass = type.resolve();

    if (psiClass == null) return;

    for (PsiClass aClass : GroovyUtils.iterateSupers(psiClass, true)) {
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
    myTypeComboBox.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        System.out.println("");
      }

      public void keyPressed(KeyEvent e) {
        if (KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK).getKeyCode() == e.getKeyCode()) {
          trimDocumentText();
        }
      }

      public void keyReleased(KeyEvent e) {
        System.out.println("");
      }
    });

    editorTextField.addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireDataChanged();
      }
    });

    final PsiClassType objectType = TypesUtil.createJavaLangObject(myReferenceExpression);
    myTypeComboBox.getEditor().setItem(createDocument(objectType.getPresentableText()));
  }

  protected void addDataChangeListener() {
    myListenerList.add(DataChangedListener.class, new DataChangedListener());
  }

  private void trimDocumentText() {
    final Document typeEditorDocument = getTypeEditorDocument();

    if (typeEditorDocument == null) return;

    final String text = typeEditorDocument.getText();
    typeEditorDocument.setText(text.trim());
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

      final PsiType type = typeElement.getType();
      if (type instanceof PsiClassType && ((PsiClassType) type).resolve() == null) {
        setStatusTextAndIcon(IconLoader.getIcon("/compiler/warning.png"), GroovyInspectionBundle.message("unresolved.type.status", type.getPresentableText()));
      } else {
        setStatusTextAndIcon(IconLoader.getIcon("/compiler/information.png"), GroovyInspectionBundle.message("resolved.type.status", type.getPresentableText()));
      }
    }
  }

  @Nullable
  public GrTypeElement getEnteredTypeName() {
    final Document typeEditorDocument = getTypeEditorDocument();

    if (typeEditorDocument == null) return null;

    final String text = typeEditorDocument.getText();
    if (!text.matches(DynamicToolWindowWrapper.QUALIFIED_IDENTIFIER_REGEXP)) return null;
    try {
      return GroovyPsiElementFactory.getInstance(myProject).createTypeElement(text);
    } catch (IncorrectOperationException e) {
      return null;
    }

  }

  public Document getTypeEditorDocument() {
    final Object item = myTypeComboBox.getEditor().getItem();

    return item instanceof Document ? (Document) item : null;

  }

  public ContainingClassItem getEnteredContaningClass() {
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
    GrTypeElement typeElement = getEnteredTypeName();

    if (typeElement == null) {
      myDynamicElement.setType("java.lang.Object");
    } else {
      PsiType type = typeElement.getType();
      if (type instanceof PsiPrimitiveType) {
        type = TypesUtil.boxPrimitiveType(type, typeElement.getManager(), myProject.getAllScope());
      }

      final String typeQualifiedName = type.getCanonicalText();

      if (typeQualifiedName != null) {
        myDynamicElement.setType(typeQualifiedName);
      } else {
        myDynamicElement.setType(type.getPresentableText());
      }
    }

    final DynamicManager dynamicManager = DynamicManager.getInstance(myProject);
    final DClassElement classElement = dynamicManager.getOrCreateClassElement(myModule, getEnteredContaningClass().getContainingClass().getQualifiedName(), true);

    if (myDynamicElement instanceof DMethodElement) {
      dynamicManager.addMethod(classElement, ((DMethodElement) myDynamicElement));
    } else if (myDynamicElement instanceof DPropertyElement) {
      dynamicManager.addProperty(classElement, ((DPropertyElement) myDynamicElement));
    }

//    myDynamicManager.fireChange();
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

  protected JPanel getPanel() {
    return myPanel;
  }

  protected boolean isTableVisible() {
    return false;
  }

  public JTable getTable() {
    return myTable;
  }

  protected boolean isTypeChekerPanelEnable() {
    return false;
  }

  public Project getProject() {
    return myProject;
  }

  protected void setUpTypeLabel(String typeLabelText) {
    myTypeLabel.setText(typeLabelText);
  }
}