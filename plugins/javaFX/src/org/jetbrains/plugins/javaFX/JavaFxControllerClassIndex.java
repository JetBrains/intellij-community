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
package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Function;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFXNamespaceProvider;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

public class JavaFxControllerClassIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("JavaFxControllerClassIndex");
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();
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

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

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
    return 0;
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    private static final SAXParser SAX_PARSER = createParser();

    private static SAXParser createParser() {
      try {
        return SAXParserFactory.newInstance().newSAXParser();
      }
      catch (Exception e) {
        return null;
      }
    }

    @Override
    @NotNull
    public Map<String, Void> map(final FileContent inputData) {
      final String className = getControllerClassName(inputData.getContentAsText().toString());
      if (className != null) {
        return Collections.singletonMap(className, null);
      }
      return Collections.emptyMap();
    }

    @Nullable
    private static String getControllerClassName(String content) {
      if (!content.contains(JavaFXNamespaceProvider.JAVAFX_NAMESPACE)) {
        return null;
      }

      final String[] className = new String[]{null};
      try {
        SAX_PARSER.parse(new InputSource(new StringReader(content)), new DefaultHandler() {
          public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            className[0] = attributes.getValue("", FxmlConstants.FX_CONTROLLER);
            if (className[0] == null) {
              if (FxmlConstants.FX_ROOT.equals(qName)) {
                className[0] = attributes.getValue("", FxmlConstants.TYPE);
              }
            }
            throw new SAXException("controllers are accepted on top level only");
          }
        });
      }
      catch (Exception e) {
        // Do nothing.
      }

      return className[0];
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    @Override
    public boolean acceptInput(final VirtualFile file) {
      return JavaFxFileTypeFactory.isFxml(file);
    }
  }

  public static List<PsiFile> findFxmlWithController(final Project project, @NotNull String className) {
    return findFxmlWithController(project, className, new Function<VirtualFile, PsiFile>() {
      @Override
      public PsiFile fun(VirtualFile file) {
        return PsiManager.getInstance(project).findFile(file);
      }
    }, ProjectScope.getAllScope(project));
  }

  public static List<VirtualFile> findFxmlsWithController(final Project project, @NotNull String className) {
    return findFxmlWithController(project, className, Function.ID, ProjectScope.getAllScope(project));
  }

  public static <T> List<T> findFxmlWithController(final Project project,
                                                     final String className,
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
