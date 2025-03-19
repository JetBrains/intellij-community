// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public final class JavaFxIdsIndex extends ScalarIndexExtension<String> {

  public static final @NonNls ID<String, Void> KEY = ID.create("javafx.id.name");

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return new FxmlDataIndexer();
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new JavaFxControllerClassIndex.MyInputFilter();
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
    return 3;
  }

  public static @NotNull Collection<String> getAllRegisteredIds(Project project) {
    CommonProcessors.CollectUniquesProcessor<String> processor = new CommonProcessors.CollectUniquesProcessor<>();
    FileBasedIndex.getInstance().processAllKeys(KEY, processor, project);
    final Collection<String> results = new ArrayList<>(processor.getResults());
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    for (Iterator<String> iterator = results.iterator(); iterator.hasNext(); ) {
      final String id = iterator.next();
      if (FileBasedIndex.getInstance().processValues(KEY, id, null, (file, value) -> false, searchScope)) {
        iterator.remove();
      }
    }
    return results;
  }

  public static @NotNull Collection<VirtualFile> getContainingFiles(Project project, String id) {
    return FileBasedIndex.getInstance().getContainingFiles(KEY, id, GlobalSearchScope.projectScope(project));
  }
}