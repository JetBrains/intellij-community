/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlUtil;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

import java.util.*;

public class JavaFxCustomComponentsIndex extends FileBasedIndexExtension<String, Set<String>> {

  @NonNls public static final ID<String, Set<String>> KEY = ID.create("javafx.custom.component");

  private final FileBasedIndex.InputFilter myInputFilter = new JavaFxControllerClassIndex.MyInputFilter();
  private final FxmlDataIndexer myDataIndexer = new FxmlDataIndexer() {
    @Override
    protected IXMLBuilder createParseHandler(final String path, final Map<String, Set<String>> map) {
      return new NanoXmlUtil.IXMLBuilderAdapter() {
        public boolean myFxRootUsed = false;

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
          if (!myFxRootUsed) {
            throw new StopException();
          }
          if (value != null && FxmlConstants.TYPE.equals(key)) {
            Set<String> paths = map.get(value);
            if (paths == null) {
              paths = new HashSet<>();
              map.put(value, paths);
            }
            paths.add(path);
          }
        }

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
          myFxRootUsed = FxmlConstants.FX_ROOT.equals(nsPrefix + ":" + name);
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          throw new StopException();
        }
      };
    }
  };
  private final FxmlDataExternalizer myDataExternalizer = new FxmlDataExternalizer();

  @NotNull
  @Override
  public DataIndexer<String, Set<String>, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @NotNull
  @Override
  public DataExternalizer<Set<String>> getValueExternalizer() {
    return myDataExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @NotNull
  @Override
  public ID<String, Set<String>> getName() {
    return KEY;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  public static <T> List<T> findCustomFxml(final Project project,
                                           @NotNull final String className,
                                           final Function<VirtualFile, T> f,
                                           final GlobalSearchScope scope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<T>>() {
      @Override
      public List<T> compute() {
        final Collection<VirtualFile> files;
        try {
          files = FileBasedIndex.getInstance().getContainingFiles(KEY, className,
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
