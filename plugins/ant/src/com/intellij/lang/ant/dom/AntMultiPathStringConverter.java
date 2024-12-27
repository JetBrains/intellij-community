// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class AntMultiPathStringConverter extends Converter<List<File>> implements CustomReferenceConverter<List<File>> {

  @Override
  public List<File> fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    final String path = attribValue.getStringValue();
    if (path == null) {
      return null;
    }
    final List<File> result = new ArrayList<>();
    Computable<String> basedirComputable = null;
    final PathTokenizer pathTokenizer = new PathTokenizer(path);
    while (pathTokenizer.hasMoreTokens()) {
      File file = new File(pathTokenizer.nextToken());
      if (!file.isAbsolute()) {
        if (basedirComputable == null) {
          basedirComputable = new Computable<>() {
            final String myBaseDir;

            {
              final AntDomProject antProject = getEffectiveAntProject(attribValue);
              myBaseDir = antProject != null ? antProject.getProjectBasedirPath() : null;
            }

            @Override
            public String compute() {
              return myBaseDir;
            }
          };
        }
        final String baseDir = basedirComputable.compute();
        if (baseDir == null) {
          continue;
        }
        file = new File(baseDir, path);
      }
      result.add(file);
    }
    return result;
  }

  private static AntDomProject getEffectiveAntProject(GenericAttributeValue attribValue) {
    AntDomProject project = attribValue.getParentOfType(AntDomProject.class, false);
    if (project != null) {
      project = project.getContextAntProject();
    }
    return project;
  }

  @Override
  public String toString(@Nullable List<File> files, @NotNull ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }

  @Override
  public PsiReference @NotNull [] createReferences(GenericDomValue<List<File>> genericDomValue, PsiElement element, ConvertContext context) {
    final GenericAttributeValue attributeValue = (GenericAttributeValue)genericDomValue;

    final String cpString = genericDomValue.getRawText();
    if (cpString == null || cpString.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    final List<PsiReference> result = new ArrayList<>();
    final PathTokenizer pathTokenizer = new PathTokenizer(cpString);
    int searchFromIndex = 0;
    while (pathTokenizer.hasMoreTokens()) {
      final String path = pathTokenizer.nextToken();
      if (!path.isEmpty()) {
        final int pathBeginIndex = cpString.indexOf(path, searchFromIndex);
        final AntDomFileReferenceSet refSet = new AntDomFileReferenceSet(attributeValue, path, pathBeginIndex, false);
        ContainerUtil.addAll(result, refSet.getAllReferences());
        searchFromIndex = pathBeginIndex;
      }
    }

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }
}
