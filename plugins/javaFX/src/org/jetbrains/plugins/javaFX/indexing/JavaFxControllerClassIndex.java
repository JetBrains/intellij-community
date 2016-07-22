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
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.Processor;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxNamespaceDataProvider;

import java.io.StringReader;
import java.util.*;

public class JavaFxControllerClassIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("JavaFxControllerClassIndex");
  private final MyInputFilter myInputFilter = new MyInputFilter();
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
    return EnumeratorStringDescriptor.INSTANCE;
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
      final String className = getControllerClassName(inputData.getContentAsText().toString());
      if (className != null) {
        return Collections.singletonMap(className, null);
      }
      return Collections.emptyMap();
    }

    @Nullable
    private static String getControllerClassName(String content) {
      if (!content.contains(JavaFxNamespaceDataProvider.JAVAFX_NAMESPACE)) {
        return null;
      }

      final String[] className = new String[]{null};
      
      class StopException extends RuntimeException {}

      try {
        NanoXmlUtil.parse(new StringReader(content), new NanoXmlUtil.IXMLBuilderAdapter() {
          private boolean myFxRootUsed = false;

          @Override
          public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
            if (value != null &&
                (FxmlConstants.FX_CONTROLLER.equals(nsPrefix + ":" + key) || FxmlConstants.TYPE.equals(key) && myFxRootUsed)) {
              className[0] = value;
            }
          }

          @Override
          public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
            throw new StopException();
          }

          @Override
          public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
            throws Exception {
            myFxRootUsed = FxmlConstants.FX_ROOT.equals(nsPrefix + ":" + name);
          }
        });
      }
      catch (StopException ignore){}
      return className[0];
    }
  }

  public static class MyInputFilter extends DefaultFileTypeSpecificInputFilter {
    public MyInputFilter() {
      super(StdFileTypes.XML);
    }
    @Override
    public boolean acceptInput(@NotNull final VirtualFile file) {
      return JavaFxFileTypeFactory.isFxml(file);
    }
  }

  public static List<PsiFile> findFxmlWithController(final Project project, @NotNull String className) {
    return findFxmlWithController(project, className, ProjectScope.getAllScope(project));
  }

  public static List<PsiFile> findFxmlWithController(final Project project, @NotNull String className, @NotNull GlobalSearchScope scope) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return findFxmlWithController(project, className, psiManager::findFile, scope);
  }

  public static List<VirtualFile> findFxmlsWithController(final Project project, @NotNull String className) {
    return findFxmlsWithController(project, className, ProjectScope.getAllScope(project));
  }

  public static List<VirtualFile> findFxmlsWithController(final Project project,
                                                          @NotNull String className,
                                                          @NotNull GlobalSearchScope scope) {
    return findFxmlWithController(project, className, Functions.id(), scope);
  }

  private static <T> List<T> findFxmlWithController(final Project project,
                                                     @NotNull final String className,
                                                     final Function<VirtualFile, T> f,
                                                     final GlobalSearchScope scope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<T>>() {
      @Override
      public List<T> compute() {
        final Collection<VirtualFile> files;
        try {
          files = FileBasedIndex.getInstance().getContainingFiles(NAME, className,
                                                                  GlobalSearchScope.projectScope(project).intersectWith(scope));
        }
        catch (IndexNotReadyException e) {
          return Collections.emptyList();
        }
        if (files.isEmpty()) return Collections.emptyList();
        List<T> result = new ArrayList<>();
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
