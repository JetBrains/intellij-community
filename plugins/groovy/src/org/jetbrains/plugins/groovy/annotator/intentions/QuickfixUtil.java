package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.*;

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

  public static String[] getMethodArgumentsNames(Project project, PsiType[] types) {
    List<String> unicNames = new ArrayList<String>();
    for (PsiType type : types) {
      final SuggestedNameInfo nameInfo = CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, null, type);

      final String name = nameInfo.names[0] + "<0";
      if (unicNames.contains(name)) {
        final String[] strings = name.split("<");
        final String realName = strings[0];
        final String count = strings[1];
        final int i = Integer.parseInt(count);
        unicNames.add(realName + "<" + (i + 1));
        continue;
      }

      unicNames.add(name);
    }

    List<String> names = new ArrayList<String>();
    for (String unicName : unicNames) {
      final String[] strings = unicName.split("<");
      final String realName = strings[0];
      final String countStr = strings[1];

      final String name;
      if ("0".equals(countStr)) {
        name = realName;
      } else {
        name = realName + countStr;
      }
      names.add(name);
    }

    return names.toArray(new String[names.size()]);
  }

  public static List<MyPair> swapArgumentsAndTypes(String[] names, PsiType[] types) {
    List<MyPair> result = new ArrayList<MyPair>();

    if (names.length != types.length) return null;

    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      result.add(new MyPair(name, types[i].getCanonicalText()));
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

  public static String[] getArgumentsNames(List<MyPair> listOfPairs) {
    final ArrayList<String> result = new ArrayList<String>();
    for (MyPair listOfPair : listOfPairs) {
      result.add(listOfPair.first);
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