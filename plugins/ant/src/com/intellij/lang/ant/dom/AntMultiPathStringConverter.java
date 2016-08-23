/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.apache.tools.ant.PathTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2010
 */
public class AntMultiPathStringConverter extends Converter<List<File>> implements CustomReferenceConverter<List<File>> {

  public List<File> fromString(@Nullable @NonNls String s, ConvertContext context) {
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
          basedirComputable = new Computable<String>() {
            final String myBaseDir;
            {
              final AntDomProject antProject = getEffectiveAntProject(attribValue);
              myBaseDir = antProject != null? antProject.getProjectBasedirPath() : null;
            }

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

  public String toString(@Nullable List<File> files, ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<List<File>> genericDomValue, PsiElement element, ConvertContext context) {
    final GenericAttributeValue attributeValue = (GenericAttributeValue)genericDomValue;

    final String cpString = genericDomValue.getRawText();
    if (cpString == null || cpString.length() == 0) {
      return PsiReference.EMPTY_ARRAY;
    }

    final List<PsiReference> result = new ArrayList<>();
    final PathTokenizer pathTokenizer = new PathTokenizer(cpString);
    int searchFromIndex = 0;
    while (pathTokenizer.hasMoreTokens()) {
      final String path = pathTokenizer.nextToken();
      if (path.length() > 0) {
        final int pathBeginIndex = cpString.indexOf(path, searchFromIndex);
        final AntDomFileReferenceSet refSet = new AntDomFileReferenceSet(attributeValue, path, pathBeginIndex, false);
        ContainerUtil.addAll(result, refSet.getAllReferences());
        searchFromIndex = pathBeginIndex;
      }
    }

    return result.toArray(new PsiReference[result.size()]);
  }
}
