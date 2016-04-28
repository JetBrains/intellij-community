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
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;

public class JavaFxImportsIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("JavaFxImportIndex");
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();
  private final FileBasedIndex.InputFilter myInputFilter = new JavaFxControllerClassIndex.MyInputFilter();
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();

  @Override
  @NotNull
  public ID<String, Void> getName() {
    return NAME;
  }


  @Override
  @NotNull
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    @Override
    @NotNull
    public Map<String, Void> map(@NotNull final FileContent inputData) {
      final Map<String, Void> classNames = getImportClassNames(inputData.getContentAsText().toString());
      return classNames != null ? classNames : Collections.emptyMap();
    }

    @Nullable
    private static Map<String, Void> getImportClassNames(String content) {
      final Map<String, Void> imports = new HashMap<>();

      class StopException extends RuntimeException {
      }

      try {
        NanoXmlUtil.parse(new StringReader(content), new NanoXmlUtil.IXMLBuilderAdapter() {
          @Override
          public void newProcessingInstruction(String target, Reader reader) throws Exception {
            if ("import".equals(target)) {
              final String importedClassOrPackage = StreamUtil.readTextFrom(reader);
              imports.put(importedClassOrPackage, null);
            }
          }

          @Override
          public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
            throws Exception {
            throw new StopException();
          }
        });
      }
      catch (StopException ignore) {
      }
      return imports;
    }
  }

  public static List<PsiFile> findFxmlWithImport(@NotNull final Project project, @NotNull final String className) {
    return findFxmlWithImport(project, className, file -> PsiManager.getInstance(project).findFile(file));
  }


  public static <T> List<T> findFxmlWithImport(@NotNull final Project project,
                                               @NotNull final String className,
                                               @NotNull final Function<VirtualFile, T> f) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<T>>() {

      @Override
      public List<T> compute() {
        final Set<VirtualFile> files = new HashSet<>();
        try {
          final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
          files.addAll(FileBasedIndex.getInstance().getContainingFiles(NAME, className, scope));
          final String packageName = StringUtil.getPackageName(className);
          if (!StringUtil.isEmpty(packageName)) {
            files.addAll(FileBasedIndex.getInstance().getContainingFiles(NAME, packageName + ".*", scope));
          }
        }
        catch (IndexNotReadyException e) {
          return Collections.emptyList();
        }
        if (files.isEmpty()) return Collections.emptyList();
        List<T> result = new ArrayList<T>();
        for (VirtualFile file : files) {
          if (!file.isValid()) continue;
          final T fFile = f.fun(file);
          if (fFile != null) {
            result.add(fFile);
          }
        }
        return result;
      }
    });
  }
}
