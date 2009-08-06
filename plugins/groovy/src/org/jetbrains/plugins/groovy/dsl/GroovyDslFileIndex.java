/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.PairProcessor;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.*;

/**
 * @author peter
 */
public class GroovyDslFileIndex extends ScalarIndexExtension<String> {
  private static final Key<CachedValue<GroovyDslExecutor>> CACHED_ENHANCED_KEY = Key.create("CACHED_ENHANCED_KEY");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex");
  @NonNls public static final ID<String, Void> NAME = ID.create("GroovyDslFileIndex");
  @NonNls private static final String OUR_KEY = "ourKey";
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();

  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();

  public ID<String,Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return false;
  }

  public int getVersion() {
    return 0;
  }

  private static List<GroovyFile> getDslFiles(final GlobalSearchScope scope) {
    final Project project = scope.getProject();
    assert project != null;
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, OUR_KEY, scope);
    if (files.isEmpty()) return Collections.emptyList();

    List<GroovyFile> result = new ArrayList<GroovyFile>();
    for(VirtualFile file: files) {
      if (!file.isValid()) continue;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof GroovyFile) {
        result.add((GroovyFile)psiFile);
      }
    }
    return result;
  }

  public static boolean processExecutors(PsiElement place, PairProcessor<GroovyFile, GroovyDslExecutor> consumer) {
    final PsiFile placeFile = place.getContainingFile().getOriginalFile();
    final VirtualFile placeVFfile = placeFile.getVirtualFile();
    if (placeVFfile == null) {
      return true;
    }

    for (final GroovyFile file : getDslFiles(new AdditionalIndexedRootsScope(place.getResolveScope(), StandardDslIndexedRootsProvider.class))) {
      final VirtualFile vfile = file.getVirtualFile();
      if (vfile == null || vfile.equals(placeVFfile)) {
        continue;
      }

      CachedValue<GroovyDslExecutor> cachedEnhanced = file.getUserData(CACHED_ENHANCED_KEY);
      if (cachedEnhanced == null) {
        file.putUserData(CACHED_ENHANCED_KEY, cachedEnhanced = file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<GroovyDslExecutor>() {
          public Result<GroovyDslExecutor> compute() {
            try {
              return Result.create(new GroovyDslExecutor(file.getText(), vfile.getName()), file);
            }
            catch (CompilationFailedException e) {
              LOG.error(e);
              return Result.create(null, file);
            }
          }
        }, false));
      }

      final GroovyDslExecutor value = cachedEnhanced.getValue();
      if (value != null && !consumer.process(file, value)) {
        return false;
      }
    }
    return true;
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {

    @NotNull
    public Map<String, Void> map(final FileContent inputData) {
      return Collections.singletonMap(OUR_KEY, null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return "gdsl".equals(file.getExtension());
    }
  }

}
