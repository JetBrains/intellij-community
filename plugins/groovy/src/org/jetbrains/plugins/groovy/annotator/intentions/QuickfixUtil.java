package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class QuickfixUtil {
  @Nullable
  public static PsiClass findTargetClass(GrReferenceExpression refExpr) {
    final PsiClass psiClass;
    if (refExpr.isQualified()) {
      GrExpression qualifier = refExpr.getQualifierExpression();
      PsiType type = qualifier.getType();
      if (!(type instanceof PsiClassType)) return null;

      psiClass = ((PsiClassType) type).resolve();
    } else {
      GroovyPsiElement context = PsiTreeUtil.getParentOfType(refExpr, GrTypeDefinition.class, GroovyFileBase.class);
      if (context instanceof GrTypeDefinition) return (PsiClass) context;
      else if (context instanceof GroovyFileBase) return ((GroovyFileBase) context).getScriptClass();
      return null;
    }
    return psiClass;
  }


  public static boolean ensureFileWritable(Project project, PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    final ReadonlyStatusHandler readonlyStatusHandler =
        ReadonlyStatusHandler.getInstance(project);
    final ReadonlyStatusHandler.OperationStatus operationStatus =
        readonlyStatusHandler.ensureFilesWritable(virtualFile);
    return !operationStatus.hasReadonlyFiles();
  }

  public static Editor positionCursor(Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    VirtualFile vFile = targetFile.getVirtualFile();
    assert vFile != null;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vFile, textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static PsiType[] getMethodParametersTypes(GrCallExpression methodCall) {
    final GrArgumentList argumentList = methodCall.getArgumentList();
    List<PsiType> types = new ArrayList<PsiType>();
    if (argumentList != null) {
      for (GrNamedArgument namedArgument : argumentList.getNamedArguments()) {
        types.add(namedArgument.getLabel().getExpectedArgumentType());
      }
    }

    return types.toArray(PsiType.EMPTY_ARRAY);
  }

  public static boolean isMethosCall(GrReferenceExpression referenceExpression) {
    return referenceExpression.getParent() instanceof GrMethodCallExpression;
  }
}
