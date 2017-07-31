/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase;
import com.intellij.refactoring.changeSignature.ExceptionsTableModel;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.VisibilityPanelBase;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.ui.GroovyComboboxVisibilityPanel;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrChangeSignatureDialog extends ChangeSignatureDialogBase<GrParameterInfo, PsiMethod, String, GrMethodDescriptor, GrParameterTableModelItem, GrParameterTableModel > {
  private static final Logger LOG = Logger.getInstance(GrChangeSignatureDialog.class);

  private static final String INDENT = "    ";

  private ExceptionsTableModel myExceptionsModel;

  public GrChangeSignatureDialog(Project project,
                                 GrMethodDescriptor method,
                                 boolean allowDelegation,
                                 PsiElement defaultValueContext) {
    super(project, method, allowDelegation, defaultValueContext);
  }

  @Override
  protected LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Override
  protected GrParameterTableModel createParametersInfoModel(GrMethodDescriptor method) {
    final PsiParameterList parameterList = method.getMethod().getParameterList();
    return new GrParameterTableModel(parameterList, myDefaultValueContext, this);
  }

  @Override
  protected BaseRefactoringProcessor createRefactoringProcessor() {
    final CanonicalTypes.Type type = getReturnType();
    final ThrownExceptionInfo[] exceptionInfos = myExceptionsModel.getThrownExceptions();
    final GrChangeInfoImpl info = new GrChangeInfoImpl(myMethod.getMethod(), getVisibility(), type, getMethodName(), getParameters(), exceptionInfos, isGenerateDelegate());
    return new GrChangeSignatureProcessor(myProject, info);
  }

  @NotNull
  @Override
  protected List<Pair<String, JPanel>> createAdditionalPanels() {
    // this method is invoked before constructor body
    myExceptionsModel = new ExceptionsTableModel(myMethod.getMethod().getThrowsList());
    myExceptionsModel.setTypeInfos(myMethod.getMethod());

    final JBTable table = new JBTable(myExceptionsModel);
    table.setStriped(true);
    table.setRowHeight(20);
    table.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    final JavaCodeFragmentTableCellEditor cellEditor = new JavaCodeFragmentTableCellEditor(myProject);
    cellEditor.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        final int row = table.getSelectedRow();
        final int col = table.getSelectedColumn();
        myExceptionsModel.setValueAt(cellEditor.getCellEditorValue(), row, col);
        updateSignature();
      }
    });
    table.getColumnModel().getColumn(0).setCellEditor(cellEditor);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().setSelectionInterval(0, 0);
    table.setSurrendersFocusOnKeystroke(true);

   /* myPropExceptionsButton = new AnActionButton(              //todo propagate parameters
      RefactoringBundle.message("changeSignature.propagate.exceptions.title"), null, PlatformIcons.NEW_EXCEPTION) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final Ref<JavaCallerChooser> chooser = new Ref<JavaCallerChooser>();
        Consumer<Set<PsiMethod>> callback = new Consumer<Set<PsiMethod>>() {
          @Override
          public void consume(Set<PsiMethod> psiMethods) {
            myMethodsToPropagateExceptions = psiMethods;
            myExceptionPropagationTree = chooser.get().getTree();
          }
        };
        chooser.set(new JavaCallerChooser(myMethod.getMethod(),
                                          myProject,
                                          RefactoringBundle.message("changeSignature.exception.caller.chooser"),
                                          myExceptionPropagationTree,
                                          callback));
        chooser.get().show();
      }
    };
    myPropExceptionsButton.setShortcut(CustomShortcutSet.fromString("alt X"));*/

    final JPanel panel = ToolbarDecorator.createDecorator(table).createPanel();
      //.addExtraAction(myPropExceptionsButton).createPanel();
    panel.setBorder(JBUI.Borders.empty());

    myExceptionsModel.addTableModelListener(mySignatureUpdater);

    final ArrayList<Pair<String, JPanel>> result = new ArrayList<>();
    final String message = RefactoringBundle.message("changeSignature.exceptions.panel.border.title");
    result.add(Pair.create(message, panel));
    return result;

  }

  @Nullable
  private CanonicalTypes.Type getReturnType() {
    PsiType returnType = null;
    try {
      if (myReturnTypeCodeFragment != null) {
        returnType = ((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType();
      }
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException ignored) {
    }

    return returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
  }

  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    return factory.createTypeCodeFragment(myMethod.getReturnTypeText(), myMethod.getMethod(), true, JavaCodeFragmentFactory.ALLOW_VOID);
  }

  @Nullable
  @Override
  protected CallerChooserBase<PsiMethod> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<PsiMethod>> callback) {
    return null; //todo next iteration
  }


  private boolean isGroovyMethodName(String name) {
    String methodText = "def " + name + "(){}";
    try {
      final GrMethod method = GroovyPsiElementFactory.getInstance(getProject()).createMethodFromText(methodText);
      return method != null;
    }
    catch (Throwable e) {
      return false;
    }
  }

  private static boolean checkType(PsiTypeCodeFragment typeCodeFragment, boolean allowEllipsis) {
    try {
      final PsiType type = typeCodeFragment.getType();
      return allowEllipsis || !(type instanceof PsiEllipsisType);
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return false;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return true; //Groovy accepts methods and parameters without explicit type
    }
  }


  @Nullable
  @Override
  protected String validateAndCommitData() {
    if (myReturnTypeCodeFragment != null && !checkType((PsiTypeCodeFragment)myReturnTypeCodeFragment, true)) {
      return GroovyRefactoringBundle.message("return.type.is.wrong");
    }

    List<GrParameterTableModelItem> parameterInfos = myParametersTableModel.getItems();
    int newParameterCount = parameterInfos.size();
    for (int i = 0; i < newParameterCount; i++) {
      GrParameterTableModelItem item = parameterInfos.get(i);

      String name = item.parameter.getName();
      if (!StringUtil.isJavaIdentifier(name)) {
        return GroovyRefactoringBundle.message("name.is.wrong", name);
      }

      if (!checkType((PsiTypeCodeFragment)item.typeCodeFragment, i == newParameterCount - 1)) {
        return GroovyRefactoringBundle.message("type.for.parameter.is.incorrect", name);
      }
      try {
        item.parameter.setType(((PsiTypeCodeFragment)item.typeCodeFragment).getType());
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        LOG.error(e);
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        item.parameter.setType(null);
      }

      String defaultValue = item.defaultValueCodeFragment.getText();
      final String initializer = item.initializerCodeFragment.getText();
      if (item.parameter.getOldIndex() < 0 && defaultValue.trim().isEmpty() && initializer.trim().isEmpty()) {
        return GroovyRefactoringBundle.message("specify.default.value", name);
      }

      item.parameter.setInitializer(initializer);
      item.parameter.setDefaultValue(defaultValue);
    }

    ThrownExceptionInfo[] exceptionInfos = myExceptionsModel.getThrownExceptions();
    PsiTypeCodeFragment[] typeCodeFragments = myExceptionsModel.getTypeCodeFragments();
    for (int i = 0; i < exceptionInfos.length; i++) {
      ThrownExceptionInfo exceptionInfo = exceptionInfos[i];
      PsiTypeCodeFragment typeCodeFragment = typeCodeFragments[i];
      try {
        PsiType type = typeCodeFragment.getType();
        if (!(type instanceof PsiClassType)) {
          return GroovyRefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        PsiClassType throwable = factory.createTypeByFQClassName("java.lang.Throwable", myMethod.getMethod().getResolveScope());
        if (!throwable.isAssignableFrom(type)) {
          return GroovyRefactoringBundle.message("changeSignature.not.throwable.type", typeCodeFragment.getText());
        }
        exceptionInfo.setType((PsiClassType)type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return GroovyRefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return GroovyRefactoringBundle.message("changeSignature.no.type.for.exception");
      }
    }

    return null;
  }


  private static String generateParameterText(GrParameterInfo info) {
    StringBuilder builder = new StringBuilder();
    String typeText = info.getTypeText();
    if (typeText.isEmpty()) typeText = GrModifier.DEF;
    builder.append(typeText).append(' ');
    builder.append(info.getName());

    String initializer = info.getDefaultInitializer();
    if (!StringUtil.isEmpty(initializer)) {
      builder.append(" = ").append(initializer);
    }
    return builder.toString();
  }


  @Override
  protected String calculateSignature() {
    String type = myReturnTypeCodeFragment != null ? myReturnTypeCodeFragment.getText().trim() : "";

    StringBuilder builder = new StringBuilder();
    builder.append(myVisibilityPanel.getVisibility()).append(' ');
    if (!type.isEmpty()) {
      builder.append(type).append(' ');
    }

    builder.append(GrChangeSignatureUtil.getNameWithQuotesIfNeeded(getMethodName(), getProject()));
    builder.append('(');

    final List<GrParameterInfo> infos = getParameters();
    if (!infos.isEmpty()) {
      final List<String> paramsText = ContainerUtil.map(infos, info -> generateParameterText(info));
      builder.append("\n").append(INDENT);
      builder.append(StringUtil.join(paramsText, ",\n" + INDENT));
      builder.append('\n');
    }
    builder.append(')');

    final PsiTypeCodeFragment[] exceptions = myExceptionsModel.getTypeCodeFragments();
    if (exceptions.length > 0) {
      builder.append("\nthrows\n");
      final List<String> exceptionNames = ContainerUtil.map(exceptions, fragment -> fragment.getText());

      builder.append(INDENT).append(StringUtil.join(exceptionNames, ",\n" + INDENT));
    }
    return builder.toString();
  }

  @Override
  protected VisibilityPanelBase<String> createVisibilityControl() {
    return new GroovyComboboxVisibilityPanel();
  }
}
