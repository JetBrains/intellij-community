// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlBuilder;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaFxCustomComponentsIndex extends FileBasedIndexExtension<String, Set<String>> {

  @NonNls public static final ID<String, Set<String>> KEY = ID.create("javafx.custom.component");

  private final FileBasedIndex.InputFilter myInputFilter = new JavaFxControllerClassIndex.MyInputFilter();
  private final FxmlDataIndexer myDataIndexer = new FxmlDataIndexer() {
    @Override
    protected IXMLBuilder createParseHandler(final String path, final Map<String, Set<String>> map) {
      return new NanoXmlBuilder() {
        public boolean myFxRootUsed = false;

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
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
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
          myFxRootUsed = FxmlConstants.FX_ROOT.equals(nsPrefix + ":" + name);
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) {
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
    return JavaFxControllerClassIndex.findFxmls(KEY, project, className, f, scope);
  }
}
