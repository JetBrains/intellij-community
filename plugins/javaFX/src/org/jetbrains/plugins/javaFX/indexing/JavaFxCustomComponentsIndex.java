// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlBuilder;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

import java.util.List;
import java.util.Map;

public final class JavaFxCustomComponentsIndex extends ScalarIndexExtension<String> {
  public static final @NonNls ID<String, Void> KEY = ID.create("javafx.custom.component");

  private final FileBasedIndex.InputFilter myInputFilter = new JavaFxControllerClassIndex.MyInputFilter();
  private final FxmlDataIndexer myDataIndexer = new FxmlDataIndexer() {
    @Override
    protected IXMLBuilder createParseHandler(@NotNull Map<String, Void> map) {
      return new NanoXmlBuilder() {
        public boolean myFxRootUsed = false;

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
          if (!myFxRootUsed) {
            throw new StopException();
          }
          if (value != null && FxmlConstants.TYPE.equals(key)) {
            map.put(value, null);
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

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public @NotNull ID<String, Void> getName() {
    return KEY;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 2;
  }

  public static <T> List<T> findCustomFxml(final Project project,
                                           final @NotNull String className,
                                           final Function<VirtualFile, T> f,
                                           final GlobalSearchScope scope) {
    return JavaFxControllerClassIndex.findFxmls(KEY, project, className, f, scope);
  }
}
