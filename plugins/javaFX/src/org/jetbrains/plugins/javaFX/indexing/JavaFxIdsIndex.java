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

public class JavaFxIdsIndex extends ScalarIndexExtension<String> {

  @NonNls public static final ID<String, Void> KEY = ID.create("javafx.id.name");

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new FxmlDataIndexer();
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new JavaFxControllerClassIndex.MyInputFilter();
  }

  @NotNull
  @Override
  public ID<String, Void> getName() {
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
    return 3;
  }

  @NotNull
  public static Collection<String> getAllRegisteredIds(Project project) {
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

  @NotNull
  public static Collection<VirtualFile> getContainingFiles(Project project, String id) {
    return FileBasedIndex.getInstance().getContainingFiles(KEY, id, GlobalSearchScope.projectScope(project));
  }
}