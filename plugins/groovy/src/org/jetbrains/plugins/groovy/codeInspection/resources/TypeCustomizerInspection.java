// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.resources;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.HashSet;

import static com.intellij.psi.util.PointersKt.createSmartPointer;

/**
 * @author Max Medvedev
 */
public class TypeCustomizerInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitFile(@NotNull GroovyFileBase file) {
        CompilerConfiguration configuration = CompilerConfiguration.getInstance(file.getProject());
        if (configuration != null && !configuration.isResourceFile(file.getVirtualFile()) && fileSeemsToBeTypeCustomizer(file)) {
          final LocalQuickFix[] fixes = {new AddToResourceFix(file)};
          final String message = GroovyInspectionBundle.message("type.customizer.is.not.marked.as.a.resource.file");
          registerError(file, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }


  private static final HashSet<String> CUSTOMIZER_EVENT_NAMES = ContainerUtil
    .newHashSet("setup", "finish", "unresolvedVariable", "unresolvedProperty", "unresolvedAttribute", "beforeMethodCall", "afterMethodCall",
                "onMethodSelection", "methodNotFound", "beforeVisitMethod", "afterVisitMethod", "beforeVisitClass", "afterVisitClass",
                "incompatibleAssignment");


  public static boolean fileSeemsToBeTypeCustomizer(@NotNull final PsiFile file) {
    if (file instanceof GroovyFile && ((GroovyFile)file).isScript()) {
      for (GrStatement statement : ((GroovyFile)file).getStatements()) {
        if (statement instanceof GrMethodCall) {
          GrExpression invoked = ((GrMethodCall)statement).getInvokedExpression();
          if (invoked instanceof GrReferenceExpression &&
              !((GrReferenceExpression)invoked).isQualified() &&
              isCustomizerEvent(((GrReferenceExpression)invoked).getReferenceName())) {
            return true;
          }
        }
      }
    }

    return false;
  }
  private static boolean isCustomizerEvent(@Nullable String name) {
    return CUSTOMIZER_EVENT_NAMES.contains(name);
  }

  private static class AddToResourceFix implements LocalQuickFix {

    private final @NotNull SmartPsiElementPointer<PsiFile> myFilePointer;

    public AddToResourceFix(@NotNull PsiFile file) {
      myFilePointer = createSmartPointer(file);
    }

    @NotNull
    @Override
    public String getName() {
      return GroovyInspectionBundle.message("add.to.resources");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyInspectionBundle.message("add.type.customizer.to.resources");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = myFilePointer.getElement();
      if (file == null) return;

      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) return;

      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      final VirtualFile contentRoot = fileIndex.getContentRootForFile(virtualFile);
      if (contentRoot == null) return;

      final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(virtualFile);
      if (sourceRoot == null) {
        final String path = VfsUtilCore.getRelativePath(virtualFile, contentRoot, '/');
        CompilerConfiguration.getInstance(project).addResourceFilePattern(path);
      }
      else {
        final String path = VfsUtilCore.getRelativePath(virtualFile, sourceRoot, '/');
        final String sourceRootPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '/');
        CompilerConfiguration.getInstance(project).addResourceFilePattern(sourceRootPath + ':' + path);
      }
      DaemonCodeAnalyzer.getInstance(project).restart(file);
    }
  }
}
