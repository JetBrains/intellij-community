/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class QuickfixUtil {
  @Nullable
  public static PsiClass findTargetClass(GrReferenceExpression refExpr, boolean compileStatic) {
    if (refExpr.getQualifier() == null) {
      return PsiUtil.getContextClass(refExpr);
    }

    PsiType type = GrReferenceResolveUtil.getQualifierType(refExpr);

    if (type == null && compileStatic) {
      return GroovyPsiManager.getInstance(refExpr.getProject()).findClassWithCache(CommonClassNames.JAVA_LANG_OBJECT, refExpr.getResolveScope());
    }
    if (!(type instanceof PsiClassType)) return null;
    return ((PsiClassType)type).resolve();
  }

  public static boolean isStaticCall(GrReferenceExpression refExpr) {

    //todo: look more carefully
    GrExpression qualifierExpression = refExpr.getQualifierExpression();

    if (!(qualifierExpression instanceof GrReferenceExpression)) return false;

    GrReferenceExpression referenceExpression = (GrReferenceExpression)qualifierExpression;
    GroovyPsiElement resolvedElement = ResolveUtil.resolveProperty(referenceExpression, referenceExpression.getReferenceName());

    if (resolvedElement == null) return false;
    if (resolvedElement instanceof PsiClass) return true;

    return false;
  }


  public static boolean ensureFileWritable(Project project, PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    final ReadonlyStatusHandler readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project);
    final ReadonlyStatusHandler.OperationStatus operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile);
    return !operationStatus.hasReadonlyFiles();
  }

  public static Editor positionCursor(@NotNull Project project, @NotNull PsiFile targetFile, @NotNull PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    VirtualFile vFile = targetFile.getVirtualFile();
    assert vFile != null;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vFile, textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static String[] getMethodArgumentsNames(Project project, PsiType[] types) {
    Set<String> uniqNames = new LinkedHashSet<String>();
    Set<String> nonUniqNames = new THashSet<String>();
    for (PsiType type : types) {
      final SuggestedNameInfo nameInfo =
        JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, null, type);

      final String name = nameInfo.names[0];
      if (uniqNames.contains(name)) {
        int i = 2;
        while (uniqNames.contains(name + i)) i++;
        uniqNames.add(name + i);
        nonUniqNames.add(name);
      } else {
        uniqNames.add(name);
      }
    }

    final String[] result = new String[uniqNames.size()];
    int i = 0;
    for (String name : uniqNames) {
      result[i] = nonUniqNames.contains(name) ? name + 1 : name;
      i++;
    }
    return result;
  }

  public static List<ParamInfo> swapArgumentsAndTypes(String[] names, PsiType[] types) {
    List<ParamInfo> result = new ArrayList<ParamInfo>();

    if (names.length != types.length) return Collections.emptyList();

    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      final PsiType type = types[i];

      result.add(new ParamInfo(name, type.getCanonicalText()));
    }

    return result;
  }

  public static boolean isCall(GrReferenceExpression referenceExpression) {
    return referenceExpression.getParent() instanceof GrCall;
  }

  public static String[] getArgumentsTypes(List<ParamInfo> listOfPairs) {
    final List<String> result = new ArrayList<String>();

    if (listOfPairs == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    for (ParamInfo listOfPair : listOfPairs) {
      String type = PsiTypesUtil.unboxIfPossible(listOfPair.type);
      result.add(type);
    }

    return ArrayUtil.toStringArray(result);
  }

  public static String[] getArgumentsNames(List<ParamInfo> listOfPairs) {
    final ArrayList<String> result = new ArrayList<String>();
    for (ParamInfo listOfPair : listOfPairs) {
      String name = listOfPair.name;
      result.add(name);
    }

    return ArrayUtil.toStringArray(result);
  }

  public static String shortenType(String typeText) {
    if (typeText == null) return "";
    final int i = typeText.lastIndexOf(".");
    if (i != -1) {
      return typeText.substring(i + 1);
    }
    return typeText;
  }

  public static DynamicElementSettings createSettings(GrReferenceExpression referenceExpression) {
    DynamicElementSettings settings = new DynamicElementSettings();
    final PsiClass containingClass = findTargetClass(referenceExpression, false);

    assert containingClass != null;
    String className = containingClass.getQualifiedName();
    className = className == null ? containingClass.getContainingFile().getName() : className;

    if (isStaticCall(referenceExpression)) {
      settings.setStatic(true);
    }

    settings.setContainingClassName(className);
    settings.setName(referenceExpression.getReferenceName());

    if (isCall(referenceExpression)) {
      List<PsiType> unboxedTypes = new ArrayList<PsiType>();
      for (PsiType type : PsiUtil.getArgumentTypes(referenceExpression, false)) {
        unboxedTypes.add(TypesUtil.unboxPrimitiveTypeWrapperAndEraseGenerics(type));
      }
      final PsiType[] types = unboxedTypes.toArray(PsiType.createArray(unboxedTypes.size()));
      final String[] names = getMethodArgumentsNames(referenceExpression.getProject(), types);
      final List<ParamInfo> infos = swapArgumentsAndTypes(names, types);

      settings.setMethod(true);
      settings.setParams(infos);
    } else {
      settings.setMethod(false);
    }
    return settings;
  }

  public static DynamicElementSettings createSettings(GrArgumentLabel label, PsiClass targetClass) {
    DynamicElementSettings settings = new DynamicElementSettings();

    assert targetClass != null;
    String className = targetClass.getQualifiedName();
    className = className == null ? targetClass.getContainingFile().getName() : className;

    settings.setContainingClassName(className);
    settings.setName(label.getName());

    return settings;
  }
}