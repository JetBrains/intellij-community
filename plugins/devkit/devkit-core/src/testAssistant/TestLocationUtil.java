// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TestLocationUtil {

  @NotNull
  static List<Location> collectRelativeLocations(Project project, VirtualFile file) {
    if (DumbService.isDumb(project)) return Collections.emptyList();

    final List<Location> locations = new ArrayList<>();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInContent(file) && !fileIndex.isInSource(file) && !fileIndex.isInLibraryClasses(file)) {
      final VirtualFile parent = file.getParent();
      final VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
      if (contentRoot != null && parent != null) {
        final String relativePath = VfsUtilCore.getRelativePath(parent, contentRoot, '/');
        if (relativePath != null) {
          final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
          final List<String> words = StringUtil.getWordsIn(relativePath);
          // put longer strings first
          words.sort((o1, o2) -> o2.length() - o1.length());

          final GlobalSearchScope testScope = GlobalSearchScopesCore.projectTestScope(project);
          Set<PsiFile> resultFiles = null;
          for (String word : words) {
            if (word.length() < 5) {
              continue;
            }
            final Set<PsiFile> files = new HashSet<>();
            searchHelper.processAllFilesWithWordInLiterals(word, testScope, new CommonProcessors.CollectProcessor<>(files));
            if (resultFiles == null) {
              resultFiles = files;
            }
            else {
              resultFiles.retainAll(files);
            }
            if (resultFiles.isEmpty()) break;
          }
          if (resultFiles != null) {
            for (Iterator<PsiFile> iterator = resultFiles.iterator(); iterator.hasNext(); ) {
              if (!VfsUtilCore.isAncestor(contentRoot, iterator.next().getVirtualFile(), true)) {
                iterator.remove();
              }
            }

            final String fileName = file.getName();
            final String nameWithoutExtension = file.getNameWithoutExtension();


            for (PsiFile resultFile : resultFiles) {
              if (resultFile instanceof PsiClassOwner) {
                final PsiClass[] classes = ((PsiClassOwner)resultFile).getClasses();
                if (classes.length > 0) {
                  ContainerUtil.addIfNotNull(locations, getLocation(project, fileName, nameWithoutExtension, classes[0]));
                }
              }
            }
          }
        }
      }
    }
    return locations;
  }

  @Nullable
  private static Location getLocation(Project project,
                                      String fileName,
                                      String nameWithoutExtension,
                                      PsiClass aClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, TestFrameworkConstants.TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME);
    if (annotation != null) {
      final Location parameterizedLocation =
        PsiMemberParameterizedLocation.getParameterizedLocation(aClass, "[" + fileName + "]", TestFrameworkConstants.PARAMETERIZED_ANNOTATION_QUALIFIED_NAME);
      if (parameterizedLocation != null) {
        return parameterizedLocation;
      }
      if (StringUtil.isJavaIdentifier(nameWithoutExtension)) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiMethod method = aClass.findMethodBySignature(elementFactory.createMethod("test" + nameWithoutExtension, PsiTypes.voidType()), true);
        if (method != null) {
          return MethodLocation.elementInClass(method, aClass);
        }

        method = aClass.findMethodBySignature(elementFactory.createMethod("test" + StringUtil.capitalize(nameWithoutExtension),
                                                                          PsiTypes.voidType()), true);
        if (method != null) {
          return MethodLocation.elementInClass(method, aClass);
        }
      }
      return new PsiLocation<PsiElement>(project, aClass);
    }
    return null;
  }
}
