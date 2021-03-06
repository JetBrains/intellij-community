// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.changeSignature.ParameterTableModelBase;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.refactoring.ui.GrCodeFragmentTableCellEditor;

import javax.swing.table.TableCellEditor;

/**
 * @author Max Medvedev
 */
public class GrParameterTableModel extends ParameterTableModelBase<GrParameterInfo, GrParameterTableModelItem>{
  public GrParameterTableModel(final PsiElement typeContext, PsiElement defaultValueContext, final GrChangeSignatureDialog dialog) {
    this(typeContext, defaultValueContext,
         new GrTypeColumn(typeContext.getProject()),
         new NameColumn<GrParameterInfo, GrParameterTableModelItem>(typeContext.getProject()),
         new GrInitializerColumn(typeContext.getProject()),
         new GrDefaultValueColumn(typeContext.getProject()),
         new AnyVarColumn<GrParameterInfo, GrParameterTableModelItem>() {
           @Override
           public boolean isCellEditable(GrParameterTableModelItem item) {
             boolean isGenerateDelegate = dialog.isGenerateDelegate();
             return !isGenerateDelegate && super.isCellEditable(item);
           }
         });
  }

  private GrParameterTableModel(PsiElement typeContext, PsiElement defaultValueContext, ColumnInfo... columnInfos) {
    super(typeContext, defaultValueContext, columnInfos);
  }


  @Override
  protected GrParameterTableModelItem createRowItem(@Nullable GrParameterInfo parameterInfo) {
    return GrParameterTableModelItem.create(parameterInfo, myTypeContext.getProject(), myDefaultValueContext);
  }

  private static class GrTypeColumn extends TypeColumn<GrParameterInfo, GrParameterTableModelItem> {

    GrTypeColumn(Project project) {
      super(project, GroovyFileType.GROOVY_FILE_TYPE, GroovyBundle.message("change.signature.type.column"));
    }

    @Override
    public TableCellEditor doCreateEditor(GrParameterTableModelItem o) {
      return new JavaCodeFragmentTableCellEditor(myProject);
    }
  }

  private static class GrDefaultValueColumn extends DefaultValueColumn<GrParameterInfo, GrParameterTableModelItem> {
    private final Project myProject;

    GrDefaultValueColumn(Project project) {
      super(project, GroovyFileType.GROOVY_FILE_TYPE);
      myProject = project;
    }

    @Override
    public TableCellEditor doCreateEditor(GrParameterTableModelItem item) {
      return new GrCodeFragmentTableCellEditor(myProject);
    }
  }

  private static class GrInitializerColumn extends GrDefaultValueColumn {
    GrInitializerColumn(Project project) {
      super(project);
    }

    @Override
    public String getName() {
      return GroovyBundle.message("change.signature.default.initializer.column");
    }

    @Override
    public boolean isCellEditable(GrParameterTableModelItem item) {
      return true;
    }

    @Override
    public PsiCodeFragment valueOf(GrParameterTableModelItem item) {
      return item.initializerCodeFragment;
    }


  }
}
