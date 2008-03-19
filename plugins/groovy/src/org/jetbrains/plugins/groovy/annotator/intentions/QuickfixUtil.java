package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
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

  public static String[] getMethodArgumentsTypes(GrCallExpression methodCall) {
    final GrExpression[] argumentList = methodCall.getExpressionArguments();
    List<String> types = new ArrayList<String>();
    if (argumentList != null && argumentList.length != 0) {
      for (GrExpression expression : argumentList) {
        final PsiType type = expression.getType();
        if (type == null) {
          types.add("");
          continue;
        }
        types.add(type.getCanonicalText());
      }
    }

    return types.toArray(new String[types.size()]);
  }

  public static String[] getMethodArgumentsNames(GrCallExpression methodCall) {
    final GrExpression[] argumentList = methodCall.getExpressionArguments();
    List<String> names = new ArrayList<String>();
    if (argumentList != null && argumentList.length != 0) {
      for (GrExpression expression : argumentList) {
        final String expressionText = expression.getText();
        names.add(expressionText);
      }
    }

    return names.toArray(new String[]{""});
  }

  public static List<MyPair> swapArgumentsAndTypes(String[] names, String[] types) {
    List<MyPair> result = new ArrayList<MyPair>();

    if (names.length != types.length) return null;

    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      result.add(new MyPair(name, types[i]));
    }

    return result;
  }

  public static boolean isCall(GrReferenceExpression referenceExpression) {
    return referenceExpression.getParent() instanceof GrCall;
  }

  public static String[] getArgumentsTypes(List<MyPair> listOfPairs) {
    final List<String> result = new ArrayList<String>();

    if (listOfPairs == null) return new String[0];
    for (MyPair listOfPair : listOfPairs) {
      result.add(listOfPair.second);
    }

    return result.toArray(new String[result.size()]);
  }

  public static String[] getArgumentsNames(List<Pair<String, PsiType>> listOfPairs) {
    final ArrayList<String> result = new ArrayList<String>();
    for (Pair<String, PsiType> listOfPair : listOfPairs) {
      result.add(listOfPair.getFirst());
    }

    return result.toArray(new String[]{""});
  }

  public static String shortenType(String typeText) {
    if (typeText == null) return "";
    final int i = typeText.lastIndexOf(".");
    if (i != -1) {
      return typeText.substring(i + 1);
    }
    return typeText;
  }

  public static Module getModuleByPsiFile(PsiFile containingFile) {
    VirtualFile file;
    if (containingFile != null) {
      file = containingFile.getVirtualFile();
      if (file == null) return null;
    } else return null;

    return ProjectRootManager.getInstance(containingFile.getProject()).getFileIndex().getModuleForFile(file);
  }


}