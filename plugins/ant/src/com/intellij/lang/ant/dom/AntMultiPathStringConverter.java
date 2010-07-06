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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2010
 */
public class AntMultiPathStringConverter extends Converter<List<PsiFileSystemItem>> implements CustomReferenceConverter<List<PsiFileSystemItem>> {

  public List<PsiFileSystemItem> fromString(@Nullable @NonNls String s, ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    final String path = attribValue.getStringValue();
    if (path == null) {
      return null;
    }
    final List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
    Computable<String> basedirComputable = null;
    for (PathIterator it = new PathIterator(path); it.hasNext();) {
      final Pair<String, Integer> pair = it.next();
      File file = new File(pair.getFirst());
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
      final PsiFileSystemItem psi = toPsiFileItem(context, file);
      if (psi != null) {
        result.add(psi);
      }
    }
    return result;
  }

  @Nullable
  private static PsiFileSystemItem toPsiFileItem(ConvertContext context, File file) {
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
    if (vFile == null) {
      return null;
    }
    final PsiManager psiManager = context.getPsiManager();

    return vFile.isDirectory()? psiManager.findDirectory(vFile) : psiManager.findFile(vFile);
  }

  private static AntDomProject getEffectiveAntProject(GenericAttributeValue attribValue) {
    // todo: get context (including) project if configured 
    return attribValue.getParentOfType(AntDomProject.class, false);
  }

  public String toString(@Nullable List<PsiFileSystemItem> files, ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<List<PsiFileSystemItem>> genericDomValue, PsiElement element, ConvertContext context) {
    final GenericAttributeValue attributeValue = (GenericAttributeValue)genericDomValue;

    final String cpString = genericDomValue.getRawText();
    if (cpString == null || cpString.length() == 0) {
      return PsiReference.EMPTY_ARRAY;
    }

    final List<PsiReference> result = new ArrayList<PsiReference>();
    
    for (PathIterator it = new PathIterator(cpString); it.hasNext();) {
      final Pair<String, Integer> pair = it.next();
      final String path = pair.getFirst();
      if (path.length() > 0) {
        final AntDomFileReferenceSet refSet = new AntDomFileReferenceSet(attributeValue, path, pair.getSecond());
        result.addAll(Arrays.asList(refSet.getAllReferences()));
      }
    }

    return result.toArray(new PsiReference[result.size()]);
  }

  private static class PathIterator implements Iterator<Pair<String, Integer>> {
    private int myBegin;
    private final String myPath;

    public PathIterator(String path) {
      myPath = path;
      myBegin = 0;
    }

    public boolean hasNext() {
      return myBegin < myPath.length();
    }

    public Pair<String, Integer> next() {
      int end = myBegin + 1;
      for (;end < myPath.length(); end++) {
        if (isAtPathDelimiter(end, myPath)) {
          break;
        }
      }
      try {
        return new Pair<String, Integer>(myPath.substring(myBegin, end), myBegin);
      }
      finally {
        myBegin = end + 1;
        while (myBegin < myPath.length() && isAtPathDelimiter(myBegin, myPath)) {
          myBegin++;
        }
      }
    }

    public void remove() {
      throw new RuntimeException("'remove' operation not supported");
    }

    private static boolean isAtPathDelimiter(int index, String path) {
      final char ch = path.charAt(index);
      if (ch == ';') {
        return true;
      }
      if (ch != ':') {
        return false;
      }
      // ch == ':'
      if (!SystemInfo.isWindows) {
        return true;
      }
      final int nextIndex = index + 1;
      if (nextIndex < path.length()) {
        final char nextCh = path.charAt(nextIndex);
        if (nextCh == '/' || nextCh == '\\') {
          return false; // looks like a drive specification
        }
      }
      return true;
    }
  }
}
